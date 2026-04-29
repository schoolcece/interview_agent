"""
面试 Agent 核心服务：Planning / Interview turn / Evaluation。
每个函数对应一个 HTTP 端点，由 Java 调用触发。
"""
import json
import logging
from datetime import datetime, timezone
from typing import Any

from sqlalchemy.orm import Session

from app.core.llm import chat
from app.db.models import InterviewSession, InterviewTurn, Resume

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────────────────────
# Prompts
# ──────────────────────────────────────────────────────────────────────────────

_PLANNING_SYSTEM = """你是一位专业的技术面试官。
根据候选人的简历和面试配置，生成一份结构化的面试计划。
输出必须是合法 JSON，格式如下：
{
  "total_turns": <整数，建议 5-15>,
  "difficulty": "<easy|medium|hard>",
  "focus_areas": ["...", "..."],
  "question_sequence": [
    {"turn_no": 1, "topic": "...", "intent": "...", "suggested_question": "..."},
    ...
  ]
}
只输出 JSON，不要其他文字。"""

_INTERVIEW_SYSTEM = """你是一位专业的技术面试官，正在进行一对一面试。
根据当前面试计划和候选人回答，完成以下两件事：
1. 对当前回答打分（JSON 格式 score_detail）
2. 生成下一道问题（若面试未结束）

输出必须是合法 JSON：
{
  "score_detail": {
    "scoring_type": "open",
    "dimensions": {
      "correctness": {"score": <0-5>, "reason": "..."},
      "depth":       {"score": <0-5>, "reason": "..."},
      "expression":  {"score": <0-5>, "reason": "..."}
    },
    "overall_score": <0.0-5.0>,
    "knowledge_points": [{"kp_code": "...", "status": "hit|partial|miss"}],
    "feedback": {"strengths": ["..."], "weaknesses": ["..."], "suggested_followup_kps": ["..."]}
  },
  "feedback_text": "一句话给候选人的反馈",
  "next_question": "下一道题（若 is_complete=false）",
  "is_complete": <true|false>
}
只输出 JSON，不要其他文字。"""

_EVALUATION_SYSTEM = """你是一位资深技术面试官，请综合候选人在本次面试中所有轮次的表现，
生成一份全面的评估报告。
输出必须是合法 JSON：
{
  "overall_score": <0.0-10.0>,
  "summary": "3-5 句综合评价",
  "strengths": ["...", "..."],
  "weaknesses": ["...", "..."],
  "knowledge_gap_kps": ["kp_code", ...],
  "hiring_recommendation": "strong_yes|yes|maybe|no",
  "next_steps": ["...", "..."]
}
只输出 JSON，不要其他文字。"""


# ──────────────────────────────────────────────────────────────────────────────
# Planning
# ──────────────────────────────────────────────────────────────────────────────

def run_planning(payload: dict[str, Any], db: Session) -> dict[str, Any]:
    """
    生成面试计划，并将 plan 写回 interview_sessions 表。
    payload 字段：session_id, user_id, resume_id, config
    """
    session_id = payload["session_id"]
    resume_id = payload.get("resume_id")
    config = payload.get("config", {})

    # 读取简历摘要（若存在）——使用新的嵌套 parsed_content 结构
    resume_summary = ""
    if resume_id:
        resume = db.get(Resume, resume_id)
        if resume and resume.parsed_content:
            pc = resume.parsed_content
            basics = pc.get("basics") or {}
            skills = pc.get("skills") or []
            experiences = pc.get("experiences") or []
            projects = pc.get("projects") or []
            exp_summary = "；".join(
                f"{e.get('company','')} - {e.get('summary','')}" for e in experiences
            )
            proj_summary = "；".join(
                f"{p.get('name','')}（{p.get('summary','')}）" for p in projects
            )
            resume_summary = (
                f"候选人：{basics.get('name', '未知')}\n"
                f"技能：{', '.join(skills)}\n"
                f"工作经历：{exp_summary or '无'}\n"
                f"项目：{proj_summary or '无'}"
            )

    user_prompt = (
        f"面试配置：\n{json.dumps(config, ensure_ascii=False)}\n\n"
        f"候选人简历摘要：\n{resume_summary or '（无简历）'}\n\n"
        f"请生成面试计划。"
    )

    raw = chat(_PLANNING_SYSTEM, user_prompt, temperature=0.5)
    plan = _parse_json(raw)

    # 写回 plan 到 DB
    session = db.get(InterviewSession, session_id)
    if session:
        session.plan = plan
        session.total_turns = plan.get("total_turns", config.get("total_turns", 10))
        db.commit()

    logger.info("Planning done for session %s: %d turns", session_id, plan.get("total_turns"))
    return {"plan": plan, "first_question": _extract_first_question(plan)}


# ──────────────────────────────────────────────────────────────────────────────
# Interview turn
# ──────────────────────────────────────────────────────────────────────────────

def run_interview_turn(payload: dict[str, Any], db: Session) -> dict[str, Any]:
    """
    处理一轮答题：评分当前回答 + 返回下一道题。
    同时将 InterviewTurn 写入 DB（Python 是唯一写者）。
    payload 字段：session_id, user_id, user_response, current_turn, audio_path
    """
    session_id = payload["session_id"]
    user_response = payload.get("user_response", "")
    current_turn_no = payload.get("current_turn", 1)

    session = db.get(InterviewSession, session_id)
    if not session:
        raise ValueError(f"Session {session_id} not found")

    plan = session.plan or {}
    question_sequence = plan.get("question_sequence", [])
    current_q = next(
        (q for q in question_sequence if q.get("turn_no") == current_turn_no),
        {"suggested_question": "请介绍一下你自己", "topic": "自我介绍"},
    )

    user_prompt = (
        f"面试计划摘要：{json.dumps(plan.get('focus_areas', []), ensure_ascii=False)}\n"
        f"当前轮次：{current_turn_no}/{session.total_turns}\n"
        f"当前问题：{current_q.get('suggested_question')}\n"
        f"候选人回答：{user_response}\n\n"
        f"请评分并决定是否继续。"
    )

    raw = chat(_INTERVIEW_SYSTEM, user_prompt)
    result = _parse_json(raw)

    is_complete = result.get("is_complete", False) or current_turn_no >= session.total_turns

    now = datetime.now(timezone.utc)

    # feedback_text 没有独立 DB 列，嵌入 score_detail 一起存储
    score_detail = result.get("score_detail") or {}
    score_detail["feedback_text"] = result.get("feedback_text", "")
    overall_score = score_detail.get("overall_score")

    # 写入 interview_turns（列名必须与 Java Flyway 建表一致）
    turn = InterviewTurn(
        session_id=session_id,
        turn_no=current_turn_no,
        question_text=current_q.get("suggested_question", ""),
        question_type="OPEN",
        user_answer_text=user_response,            # DB 列名 user_answer_text
        user_audio_path=payload.get("audio_path"), # DB 列名 user_audio_path
        score_detail=score_detail,
        score_overall=overall_score,
        status="SCORED",
        asked_at=now,                              # NOT NULL，必须写入
        answered_at=now,
        scored_at=now,
    )
    db.add(turn)
    db.commit()
    logger.info("Turn %d written for session %s", current_turn_no, session_id)

    return {
        "score_detail": result.get("score_detail"),
        "feedback_text": result.get("feedback_text"),
        "next_question": result.get("next_question"),
        "is_complete": is_complete,
    }


# ──────────────────────────────────────────────────────────────────────────────
# Evaluation
# ──────────────────────────────────────────────────────────────────────────────

def run_evaluation(payload: dict[str, Any], db: Session) -> dict[str, Any]:
    """
    面试结束后生成综合评估报告。
    payload 字段：session_id, user_id
    """
    session_id = payload["session_id"]

    turns = (
        db.query(InterviewTurn)
        .filter(InterviewTurn.session_id == session_id)
        .order_by(InterviewTurn.turn_no)
        .all()
    )

    turn_summaries = []
    for t in turns:
        sd = t.score_detail or {}
        turn_summaries.append(
            f"第{t.turn_no}轮｜问题：{t.question_text[:80]}｜"
            f"得分：{sd.get('overall_score', '?')}｜"
            f"反馈：{sd.get('feedback_text') or '无'}"
        )

    user_prompt = (
        f"共 {len(turns)} 轮面试，以下是各轮摘要：\n"
        + "\n".join(turn_summaries)
        + "\n\n请生成综合评估报告。"
    )

    raw = chat(_EVALUATION_SYSTEM, user_prompt, temperature=0.3)
    report = _parse_json(raw)

    logger.info("Evaluation done for session %s", session_id)
    return report


# ──────────────────────────────────────────────────────────────────────────────
# 工具
# ──────────────────────────────────────────────────────────────────────────────

def _parse_json(raw: str) -> dict[str, Any]:
    """从 LLM 输出中提取 JSON 对象，容忍代码块包裹。"""
    text = raw.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        text = "\n".join(lines[1:-1] if lines[-1].strip() == "```" else lines[1:])
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        logger.warning("LLM returned invalid JSON: %s\nRaw: %s", e, raw[:300])
        return {"raw": raw, "parse_error": str(e)}


def _extract_first_question(plan: dict[str, Any]) -> str:
    seq = plan.get("question_sequence", [])
    if seq:
        return seq[0].get("suggested_question", "请先做个自我介绍。")
    return "请先做个自我介绍。"
