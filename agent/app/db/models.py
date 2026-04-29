"""
SQLAlchemy ORM 模型 - 只读已有表，不负责建表（由 Java Flyway 管理）。
Python 侧写入：interview_turns；Planning 时写入 interview_sessions.plan/total_turns；
async_jobs (status/result)；resumes (parsed_content)。
Python 侧只读：users、knowledge_points 等（除 Planning 对 interview_sessions 的上述字段）。
"""
from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger, Boolean, Column, DateTime, Enum, Integer,
    JSON, Numeric, String, Text, func,
)

from app.db.database import Base


class Resume(Base):
    __tablename__ = "resumes"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger, nullable=False)
    original_filename = Column(String(255))
    file_path = Column(String(512), nullable=False)
    file_size_bytes = Column(BigInteger)
    content_hash = Column(String(64))
    raw_text = Column(Text)
    parsed_content = Column(JSON)
    mentioned_kps = Column(JSON)
    embedding_ref = Column(String(128))
    analysis_status = Column(
        # 枚举值必须与 Java AnalysisStatus 枚举及 Flyway 建表 SQL 完全一致
        Enum("PENDING", "PARSING", "PARSED", "FAILED", name="analysis_status_enum"),
        nullable=False,
        default="PENDING",
    )
    analysis_error = Column(Text)
    is_active = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime(3))


class AsyncJob(Base):
    __tablename__ = "async_jobs"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger)
    job_type = Column(String(32), nullable=False)
    related_entity_type = Column(String(32))
    related_entity_id = Column(BigInteger)
    status = Column(
        Enum("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED", name="job_status_enum"),
        nullable=False,
        default="PENDING",
    )
    progress = Column(Integer, nullable=False, default=0)
    payload = Column(JSON)
    result = Column(JSON)
    error_message = Column(Text)
    attempt_count = Column(Integer, nullable=False, default=0)
    max_attempts = Column(Integer, nullable=False, default=3)
    scheduled_at = Column(DateTime(3))
    started_at = Column(DateTime(3))
    completed_at = Column(DateTime(3))
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())


class InterviewSession(Base):
    __tablename__ = "interview_sessions"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger, nullable=False)
    resume_id = Column(BigInteger)
    title = Column(String(255))  # 与 Flyway interview_sessions.title VARCHAR(255) 一致
    status = Column(String(32), nullable=False, default="PLANNING")
    config = Column(JSON)
    plan = Column(JSON)
    current_turn = Column(Integer, nullable=False, default=0)
    total_turns = Column(Integer, nullable=False, default=10)
    duration_seconds = Column(Integer)
    version = Column(Integer, nullable=False, default=0)
    started_at = Column(DateTime(3))
    ended_at = Column(DateTime(3))
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())


class InterviewTurn(Base):
    """
    面试轮次记录。Python Agent 是唯一写入方，Java 侧只读。
    列名必须与 Java Flyway 建表 SQL（interview_turns）完全一致。
    """
    __tablename__ = "interview_turns"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    session_id = Column(BigInteger, nullable=False)
    turn_no = Column(Integer, nullable=False)
    question_id = Column(BigInteger)
    question_text = Column(Text, nullable=False)
    # 值需与 Java QuestionType 枚举一致，如 OPEN / CODING / BEHAVIORAL
    question_type = Column(String(32), nullable=False, default="OPEN")
    ask_audio_path = Column(String(512))           # AI 提问的 TTS 音频路径
    user_answer_text = Column(Text)                # 用户文字回答（原错误命名 user_response）
    user_audio_path = Column(String(512))          # 用户语音回答的音频路径（原错误命名 audio_path）
    asr_confidence = Column(Numeric(4, 3))         # ASR 置信度 0.000-1.000
    score_overall = Column(Numeric(4, 2))          # 本轮总分 0.00-5.00
    score_detail = Column(JSON)                    # 评分详情 + feedback_text 均存这里
    followup_of_turn_id = Column(BigInteger)       # 追问时指向来源轮次（原错误命名 followup_of）
    # 值需与 Java TurnStatus 枚举一致，如 PENDING / ANSWERED / SCORED
    status = Column(String(16), nullable=False, default="PENDING")
    asked_at = Column(DateTime(3), nullable=False, server_default=func.now())  # NOT NULL
    answered_at = Column(DateTime(3))
    scored_at = Column(DateTime(3))
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())
