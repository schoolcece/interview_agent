# Agent 设计专题

> 版本：v1.0（2026-04-23）
> 配套文档：`ARCHITECTURE.md`（总体架构）、`V1__init_schema.sql`（数据模型）
>
> 本文聚焦 **Python Agent 侧的设计**：Agent 结构、题库、RAG、评分、记忆、反思。Java 侧业务逻辑见 `ARCHITECTURE.md`。

---

## 目录

- [1. Agent 结构选型](#1-agent-结构选型)
- [2. 题库三层结构](#2-题库三层结构)
- [3. 出题流程](#3-出题流程)
- [4. 评分体系](#4-评分体系)
- [5. 知识掌握度与记忆](#5-知识掌握度与记忆)
- [6. 反思与闭环](#6-反思与闭环)
- [7. Prompt 工程约定](#7-prompt-工程约定)
- [8. LLM 成本与降级](#8-llm-成本与降级)

---

## 1. Agent 结构选型

### 1.1 决策：3 个 Agent，不是 5 个

旧设计有 Planning / Interview / Evaluation / Memory / Reflection 五个 Agent。实际观察发现：

- **Memory** 本质是数据库写操作（更新掌握度表），不是"Agent"
- **Reflection** 本质是基于历史数据的一次性 LLM 调用，可以合并进 Evaluation

**采用的 3 Agent 结构**：

| Agent | 触发时机 | 主要能力 | 耗时预期 |
|---|---|---|---|
| **Planner** | 面试开始时调用 1 次 | 读简历 + 读掌握度 + 生成题目计划 | 3–8s |
| **Interviewer** | 每轮对话调用 | 出题 / 追问 / 多轮状态维护 | 2–5s |
| **Evaluator** | 每轮答完立刻调 + 面试结束汇总 | 采分点评分 / rubric 打分 / 生成总评 | 3–10s |

### 1.2 Agent 之间的协作

```
    [面试开始]
         │
         ▼
   ┌──────────┐
   │ Planner  │  读: resumes, user_knowledge_mastery, question_bank
   └────┬─────┘  写: interview_sessions.plan
        │
        │ 循环 total_turns 次
        ▼
   ┌────────────┐
   │Interviewer │  读: plan, 上一轮 turn
   └────┬───────┘  写: interview_turns (status=ASKED)
        │
        │ 用户作答
        ▼
   ┌────────────┐
   │ Evaluator  │  读: question, user_answer, reference_points/rubric
   └────┬───────┘  写: interview_turns.score_detail (status=SCORED)
        │
        │ (最后一轮后)
        ▼
   ┌────────────┐
   │ Evaluator  │  读: 本 session 全部 turns
   │ (summary)  │  写: evaluation_reports
   └────────────┘
```

### 1.3 为什么不用 ReAct / Tool-calling 框架把它们串起来

研究生项目阶段**保持简单**比优雅更重要。当前做法：

- **Planner / Evaluator** 是**同步单次 LLM 调用**，不需要 ReAct 循环
- **Interviewer** 是**有状态的多轮对话**，用 LangGraph 的 StateGraph 管理状态
- 三个 Agent 之间的编排由 FastAPI 路由和 Python Service 完成，**不引入额外的 Agent 框架**

未来如果要做更复杂的工具调用（查文档、执行代码评测），再引入 tool-calling 即可。

---

## 2. 题库三层结构

### 2.1 为什么纯 LLM 生成题目不够

- 真实面试官常用的高频题 LLM 训练数据可能偏弱
- 多样性差，LLM 倾向"解释一下 xxx"式反复
- 没有难度标定，无法做自适应
- 同一题换种问法无法做错题关联

### 2.2 三层结构

```
Layer 1: 知识图谱        (knowledge_points 表)
         标签体系，每方向 150–300 个叶子节点
         ↓ 决定考什么
Layer 2: 题目库          (question_bank 表)
         题干 + 标准答案 + 采分点 + 难度
         ↓ 提供大部分题目
Layer 3: RAG 语料库      (Milvus + 外部文档)
         技术书籍、官方文档、博客段落
         ↓ 辅助追问、深度解答、推荐
```

### 2.3 Layer 1：知识图谱

**组织方式**：按方向 → 主题 → 子主题 → 叶子知识点的树形结构。

```
backend (后端)
├── java
│   ├── jvm
│   │   ├── 类加载机制          code: backend.java.jvm.classloader
│   │   ├── 垃圾回收            code: backend.java.jvm.gc
│   │   └── JMM                 code: backend.java.jvm.jmm
│   ├── concurrency
│   │   ├── aqs                 code: backend.java.concurrency.aqs
│   │   ├── threadpool          code: backend.java.concurrency.threadpool
│   │   └── concurrenthashmap   code: backend.java.concurrency.chm
│   └── framework
│       ├── spring.ioc
│       ├── spring.aop
│       └── spring.transaction
├── network
│   ├── tcp
│   │   ├── handshake           code: backend.network.tcp.handshake
│   │   ├── close_wait
│   │   └── syn_flood
│   └── http
│       ├── status_codes
│       └── tls
├── database
│   ├── mysql
│   │   ├── index_btree
│   │   ├── mvcc
│   │   └── isolation_level
│   └── redis
│       ├── persistence
│       └── sentinel
└── middleware
    └── mq
        ├── kafka.partition
        └── rocketmq.delay
```

**构建方式（现实可行）**：
1. 先手工列出每个方向的一级主题（约 10 个）
2. LLM 辅助扩展到二/三级（人工审核）
3. 每个叶子节点补上 `description` 字段（50–150 字，用于 LLM prompt）

### 2.4 Layer 2：题目库

表：`question_bank` + `question_knowledge_points`

**题目来源优先级**：
1. **手工整理核心高频题**（最高质量，~200 题/方向）
2. **LLM 基于知识图谱批量生成**（中等质量，每 KP 3–5 题，审核后入库）
3. **爬面经网站 + LLM 归一化清洗**（辅助池，低置信度，慎用）
4. **算法题直接对接 LeetCode 题号**（不自建）

**关键字段（简略）**：

```
question_bank
├── type          factual | project_based | algorithm | system_design | behavioral
├── domain        backend | ai | frontend | ...
├── difficulty    1-5
├── stem          题干
├── standard_answer     参考答案（factual 类有）
├── reference_points    采分点数组（factual 类有）
├── rubric_id           评分模板（open 类有）
├── scoring_mode        reference_points | rubric | hybrid
└── status        pending | approved | rejected (审核状态)
```

### 2.5 Layer 3：RAG 语料库

**作用**：
- 面试中用户答得不准时，Evaluator 可以引用权威资料
- Planner 生成"开放讨论题"时，从资料中找触发点
- 面试结束后生成学习推荐的"推荐理由"

**语料来源**：
- 经典技术书籍（手工收录 TOC + 正文分块）
- 官方文档（Spring / MySQL / Redis / React 等）
- 高质量博客（圆规审核）

**向量化**：
- Embedding 模型：`bge-large-zh` 或 `text-embedding-3-small`
- 分块策略：500–800 字符 + 50 overlap
- 元数据：`{source, title, section, kp_codes[], authority_score}`

**检索**：Milvus 向量检索 + BM25 关键词检索混合，RRF 融合后返回 top-k。

---

## 3. 出题流程

### 3.1 Planner：面试计划生成

**输入**：
- `resumes.parsed_content`（简历结构化）
- `resumes.mentioned_kps`（简历中出现的 KP）
- `user_knowledge_mastery`（历史掌握度）
- `interview_sessions.config`（本场配置：方向、难度区间、轮次数）

**输出写入 `interview_sessions.plan`**：

```json
{
  "strategy": "resume_driven",
  "total_turns": 10,
  "turns": [
    {
      "turn_no": 1,
      "type": "self_intro",
      "source": "llm_generated",
      "question_template": "请做一个 3 分钟的自我介绍，重点介绍最能体现你后端能力的项目"
    },
    {
      "turn_no": 2,
      "type": "project_based",
      "source": "llm_generated_followup",
      "trigger_resume_section": "projects[0]",
      "focus_kps": ["backend.database.mysql.index_btree"]
    },
    {
      "turn_no": 3,
      "type": "factual",
      "source": "question_bank",
      "question_id": 1023,
      "focus_kps": ["backend.network.tcp.handshake"]
    },
    ...
  ],
  "fallback": {
    "extra_kps": ["backend.java.concurrency.aqs", "..."]
  }
}
```

**策略选择**（简化）：
- 前 1–2 轮：自我介绍 + 针对简历中第一个项目的深挖（LLM 生成）
- 中间 60%：从题库抽题，优先薄弱 KP
- 后 20%：若用户表现好则升难度，否则保持
- 最后 1 题：开放性反问（"有什么问题问我吗"，风格贴近真实面试）

### 3.2 Interviewer：候选题生成与 ranking

每轮执行时，从 `plan` 中取出预定的 turn，但允许**动态调整**（上一轮答得差则插追问，答得好则升难度）。

**候选题三路并行（仅 type=factual 适用）**：

```
        ┌──────────────────────────────────┐
        │  当前 KP + 难度区间 + 已问题目   │
        └────────────────┬─────────────────┘
                         │
       ┌─────────────────┼─────────────────┐
       ▼                 ▼                 ▼
  Path A: 题库        Path B: LLM        Path C: 题库
  向量检索 top-K      基于 KP 生成       结构化筛选
  (相似题)            (新题, 候选)       (按 difficulty + tag)
       │                 │                 │
       └─────────────────┼─────────────────┘
                         ▼
              去重（embedding 相似度 + usage_count）
                         │
                         ▼
              LLM 最终 ranking + 改写语境
                         │
                         ▼
                  输出当前轮问题
```

**去重原则**：
- 已在 `interview_turns` 中出现过的题不再出
- 同 `stem_hash` 或 embedding 相似度 > 0.92 视为重复
- `usage_count` 高的题降权（多样性）

### 3.3 追问逻辑（project_based 类型）

用户回答项目类问题时，Evaluator 若检出以下情形则生成**追问**：

| 信号 | 追问方向 | 示例 |
|---|---|---|
| 未讲量化结果 | 问数据 | "你说缓存优化了性能，命中率从多少提升到多少？" |
| 未讲原因 | 问动机 | "为什么选 Redis 而不是本地缓存？" |
| 用了术语但未解释 | 问原理 | "你提到了 Raft，能讲讲选主流程吗？" |
| 说得太泛 | 问细节 | "遇到最难的 Bug 具体是什么，怎么定位的？" |

追问的 turn 会在 `interview_turns.followup_of_turn_id` 中关联到原 turn，形成追问链。

---

## 4. 评分体系

### 4.1 核心原则

**两类题目，两套评分方式，一套输出 schema**。

| 题目类型 | 评分方式 | 典型场景 |
|---|---|---|
| **事实类** (factual) | 采分点命中率 | "三次握手流程"、"HashMap 扩容机制" |
| **开放类** (project / behavioral / system_design) | Rubric 多维度打分 | "你最有挑战的项目是什么" |
| **算法类** (algorithm) | 代码正确性 + 思路 + 复杂度 | LeetCode 题目 |

### 4.2 事实类评分：采分点命中率

**输入**：
- 题干 `stem`
- 采分点 `reference_points`（含权重、关键词）
- 用户回答 `user_answer_text`

**LLM Prompt（骨架）**：

```
你是一位严格的技术面试评分官。请根据以下采分点，判断候选人回答的命中情况。

【题目】{stem}

【采分点】
1. {point_1} (权重 {w1}, 关键词 {keywords_1})
2. {point_2} (权重 {w2}, 关键词 {keywords_2})
...

【候选人回答】{user_answer}

请输出严格 JSON：
{
  "hits": [
    {"index": 1, "status": "hit|partial|miss", "evidence": "引用候选人原话", "reason": "..."},
    ...
  ],
  "dimensions": {
    "correctness": 1-5,
    "depth":       1-5,
    "expression":  1-5
  }
}
```

**得分计算**：

```python
overall = sum(weight_i * status_score_i)
  where status_score = {"hit": 1.0, "partial": 0.5, "miss": 0.0}
overall = round(overall * 5, 2)   # 归一到 [0, 5]
```

### 4.3 开放类评分：Rubric

**Rubric 示例**（`rubrics.dimensions`，项目经验类）：

```json
[
  {"key":"star_completeness","name":"STAR完整度",  "weight":0.25,"scale_min":1,"scale_max":5},
  {"key":"technical_depth",  "name":"技术深度",    "weight":0.30,"scale_min":1,"scale_max":5},
  {"key":"problem_solving",  "name":"问题解决",    "weight":0.25,"scale_min":1,"scale_max":5},
  {"key":"reflection",       "name":"反思能力",    "weight":0.10,"scale_min":1,"scale_max":5},
  {"key":"quantification",   "name":"量化指标",    "weight":0.10,"scale_min":1,"scale_max":5}
]
```

**自我一致性策略**：
- 同一回答让 LLM 按 rubric 独立评 **3 次**（temperature=0.3）
- 对每个维度取中位数
- 最终 `overall_score = Σ(median_score_i × weight_i)`

为什么要 3 次：LLM 单次评分方差大，3 次中位数相对稳定。代价是成本 × 3，可以对高置信度题（完全无回答等）直接跳过多次评分。

### 4.4 算法类评分

Phase 3+ 才做。思路：

- **代码提取**：从用户口述中提取代码思路关键词
- **匹配预期解法**：题库存 `expected_approaches` 数组，LLM 判断用户思路对应哪个
- **复杂度验证**：LLM 估算用户所述复杂度并与标准比对
- **代码评测**（Phase 5）：沙箱执行单元测试

MVP 阶段用户口述解法 + LLM 评分即可，不做代码评测。

### 4.5 统一输出 Schema

所有评分结果都写入 `interview_turns.score_detail`：

```json
{
  "scoring_type": "factual",
  "dimensions": {
    "correctness": {"score": 4, "reason": "准确说出了 SYN/ACK 流程"},
    "depth":       {"score": 3, "reason": "未提到半连接队列"},
    "expression":  {"score": 4, "reason": "语言组织清晰"}
  },
  "overall_score": 3.7,
  "knowledge_points": [
    {"kp_code": "backend.network.tcp.handshake",  "status": "hit"},
    {"kp_code": "backend.network.tcp.syn_flood",  "status": "miss"},
    {"kp_code": "backend.network.tcp.half_conn",  "status": "partial"}
  ],
  "feedback": {
    "strengths":  ["完整说出 SYN/SYN+ACK/ACK"],
    "weaknesses": ["未提及半连接队列与 SYN Flood 防御"],
    "suggested_followup_kps": ["backend.network.tcp.syn_flood"]
  },
  "rubric_scores": null
}
```

**下游（Java 侧）消费约定**：
- 错题本：`overall_score < 2.5` 或 `knowledge_points` 中任意 miss → 入错题本
- 掌握度：遍历 `knowledge_points`，按 hit/partial/miss 做贝叶斯更新
- 推荐：`suggested_followup_kps` 作为下次推荐的 KP 候选

---

## 5. 知识掌握度与记忆

### 5.1 为什么不做"长期对话记忆"

很多 Agent 项目会引入 LangChain ConversationBufferMemory 或向量记忆。**本项目明确不做**，理由：

- **面试场景天然有边界**：每场面试是一个独立 session，不跨场记忆
- **跨场的"记忆"就是 `user_knowledge_mastery` 表**：比向量记忆精确、可解释
- 简历里的项目经验本身就是 RAG 语料，无需额外记忆层

### 5.2 贝叶斯知识追踪（BKT）

完整算法在 `V1__init_schema.sql` 中 `user_knowledge_mastery` 表头注释，这里只讲**为什么**选择 Beta 分布：

- Beta 分布是伯努利实验（hit/miss）的**共轭先验**，更新公式数学上封闭
- `α` 可直接解释为"累计命中证据"，`β` 为"累计未命中证据"，非专业读者也易懂
- 后验均值 `α/(α+β)` 天然落在 [0,1]，可直接做"掌握度"
- 方差 `αβ/((α+β)²(α+β+1))` 可表达**不确定度**，便于"薄弱 + 证据不足"双重筛选

### 5.3 掌握度的使用

| 场景 | 查询 |
|---|---|
| 自适应出题 | `WHERE mastery_mean < 0.5 AND observations >= 2` |
| 学习推荐 | 同上，按 `mastery_mean ASC` 取 top 10 |
| 到期复习提醒 | `WHERE next_review_at < NOW()` |
| 个人画像雷达图 | 按一级域聚合 `AVG(mastery_mean)` |

### 5.4 冷启动问题

新用户 + 新知识点，`α=1, β=1, mastery_mean=0.5`。怎么办？

- **简历预热**：简历解析时，对 `mentioned_kps` 中的每个 KP 预插入一条 `α=2, β=1`（轻度先验倾向掌握），避免给所有 KP 都推相同权重
- **首次面试优先覆盖薄弱面**：Planner 第一场面试故意抽一些"简历没提到"的基础题，快速建立画像

---

## 6. 反思与闭环

### 6.1 场内反思（每轮后）

Evaluator 在给出评分的同时输出 `suggested_followup_kps`。Interviewer 在下一轮决定是否采纳：

```
if last_turn.score_overall < 2.5 and len(followup_kps) > 0:
    next_turn = 追问(followup_kps[0])
else:
    next_turn = plan.get(current_turn + 1)
```

### 6.2 场末反思（evaluation_reports）

面试结束后由 Evaluator（第二次调用）生成总评，写入 `evaluation_reports`：

- `overall_score`：各 turn 的加权平均
- `dimension_scores`：维度聚合
- `strengths` / `weaknesses`：基于各 turn 的 `score_detail.feedback` 归纳
- `kp_summary`：每个涉及 KP 的 hit/miss/partial 分布
- `professional_comment`：自由文本总评（3–5 段）
- `improvement_plan`：针对薄弱点的 2 周学习计划
- `next_suggestions`：推荐下一场面试的配置（难度、方向）

### 6.3 跨场演化

随着用户场数增加，系统应体现"记住了"的感觉：

| 用户看到 | 背后机制 |
|---|---|
| "上次你在 JVM GC 上有不足，这次我重点测这里" | Planner 读 `user_knowledge_mastery` |
| "相比上次，你对 Redis 持久化的理解深入了" | 对比历史 `kp_summary` |
| "你之前这道题答错过，这次再来" | 从 `error_problems` 取题 |
| "按你这场表现，建议 2 周后再来一场" | `next_suggestions` 基于场次间隔与进步率 |

这些"记忆感"对用户留存至关重要，也是论文可以突出的**"拟人面试官"特征**。

---

## 7. Prompt 工程约定

### 7.1 统一原则

- **中文系统 prompt**：用户基本是中文面试场景，LLM 中文能力足够
- **严格 JSON 输出**：所有 Agent 调用强制 JSON 输出，用 `response_format` 约束
- **结构化字段优先于自由文本**：能结构化的绝不让 LLM 自由发挥
- **Prompt 模板用 Jinja2**：版本化管理，放在 `python-agent/prompts/` 目录
- **记录每次调用**：prompt + response + latency + cost，落 `async_jobs` 或专用日志表

### 7.2 Prompt 版本管理

```
python-agent/prompts/
├── planner/
│   ├── v1_resume_driven.jinja2
│   └── v2_with_history.jinja2   # 历史场次加入上下文
├── interviewer/
│   ├── factual_ask.jinja2
│   ├── project_ask.jinja2
│   └── followup.jinja2
└── evaluator/
    ├── factual_score.jinja2
    ├── rubric_score.jinja2
    └── summary.jinja2
```

每次 LLM 调用记录使用的 prompt 版本，便于 A/B 测试和问题排查。

### 7.3 输出校验

- Pydantic 模型强校验
- 校验失败的原始响应落日志（便于 prompt 迭代）
- 重试 1 次（temperature 提高），仍失败则返回保底默认值

---

## 8. LLM 成本与降级

### 8.1 预期成本

单场面试 10 轮的 token 消耗（粗估，GPT-4o-mini 定价）：

| Agent | 调用次数 | 每次 input/output tokens | 小计 |
|---|---|---|---|
| Planner | 1 | 3000 / 1500 | 1 次 |
| Interviewer | 10 | 2000 / 500 | 10 次 |
| Evaluator（per turn） | 10 | 1500 / 800 | 10 次 |
| Evaluator（summary） | 1 | 8000 / 2000 | 1 次 |

单场总计约 **7–10 万 token**，GPT-4o-mini 成本约 $0.05 / 场。

### 8.2 降级策略

| 层级 | 模型选择 | 适用 |
|---|---|---|
| P0 | GPT-4o / Claude Sonnet | 评分、总评 |
| P1 | GPT-4o-mini / DeepSeek-V3 | 日常对话、追问 |
| P2 | 本地模型（Qwen 14B / GLM-4-9B） | 预算紧张或离线 |

**实现**：配置文件按 Agent 指定模型，不硬编码。

```yaml
llm:
  planner:     {provider: openai, model: gpt-4o-mini}
  interviewer: {provider: deepseek, model: deepseek-chat}
  evaluator:   {provider: openai, model: gpt-4o}
```

### 8.3 离线 / 免费方案（给师弟师妹用）

- **ASR**：faster-whisper 本地（CPU 可跑 base 模型）
- **TTS**：edge-tts（微软免费）
- **LLM**：Ollama + Qwen2.5-14B-Instruct 或使用 DeepSeek 官方 API（价格极低）
- **Embedding**：本地 `bge-large-zh`

即使不花钱也能跑通。

---

## 附录：与 Java 侧的接口契约

### A.1 async_jobs 约定

Python 通过轮询或 Redis Stream 订阅 `async_jobs` 表：

```python
# Python 侧伪代码
while True:
    jobs = fetch_pending_jobs(type="resume_parse", limit=5)
    for job in jobs:
        try:
            update_status(job.id, "RUNNING")
            result = parse_resume(job.payload)
            update_result(job.id, "SUCCESS", result)
        except Exception as e:
            update_status(job.id, "FAILED", error=str(e))
```

### A.2 面试结束事件

Python 写完 `evaluation_reports` 后发 Redis 事件：

```json
channel: "interview.session.completed"
payload: {
  "session_id": 123,
  "user_id": 45,
  "overall_score": 3.7,
  "weak_kps": ["backend.jvm.gc", "backend.network.tcp.syn_flood"]
}
```

Java 侧订阅后：
- 更新 `user_knowledge_mastery`（Phase 3 起改为事件驱动）
- 更新 `error_problems`
- 生成初始 `learning_recommendations`

Phase 1 简化做法：Python 完成后直接通过 HTTP `POST /internal/session-completed` 通知 Java，Java 同步处理后返回。

---

**维护约定**：本文档描述的算法和 schema 与代码必须一致。实现偏离必须先改文档再改代码。
