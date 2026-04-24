-- =============================================================================
-- V1__init_schema.sql  (重构版 / MySQL 8.x)
-- -----------------------------------------------------------------------------
-- AI 模拟面试系统 - 初始化数据库 Schema
--
-- 设计原则 (详见 docs/DATA_MODEL.md)
--   1. 主键统一使用 BIGINT UNSIGNED AUTO_INCREMENT，索引紧凑、写入有序。
--      UUID/ULID 仅在需要对外暴露且要避免枚举攻击时作为单独字段追加，本期不使用。
--   2. 所有结构化文本采用 MySQL 8 原生 JSON 类型，便于未来做函数索引 / 生成列。
--   3. 向量数据不入 MySQL，`embedding_ref` 仅保存外部向量库 (Milvus / pgvector)
--      的引用 key，向量本体由 Python 侧的 Agent 服务管理。
--   4. 用户隔离 = 外键归属字段 + 应用层强校验；不启用 MySQL 行级安全（不存在）。
--   5. 知识点使用语义 code (如 'backend.network.tcp.handshake') 做外部引用，
--      避免跨环境自增 ID 漂移；Java/Python 代码侧引用一律走 code。
--   6. 贝叶斯知识追踪使用 Beta(alpha, beta) 分布参数，mastery_mean 用生成列
--      物化，以支持 "按掌握度排序找薄弱点" 之类的查询。
--   7. 软删除 (deleted_at) 仅在 users / resumes / question_bank 三张主数据表
--      开启；日志/流水/派生表不做软删除，保持简单。
--
-- 迁移执行
--   本次是推翻旧 schema 的初始化。若数据库已有数据且仍在使用旧 V1：
--     mysql> DROP DATABASE interview_db; CREATE DATABASE interview_db
--            CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--     然后启动应用, Flyway 会重新执行本文件。
-- =============================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET FOREIGN_KEY_CHECKS = 0;

-- 按 FK 依赖倒序 drop, 保证可重复执行（开发环境手工 drop 用）
DROP TABLE IF EXISTS async_jobs;
DROP TABLE IF EXISTS learning_recommendations;
DROP TABLE IF EXISTS evaluation_reports;
DROP TABLE IF EXISTS error_problems;
DROP TABLE IF EXISTS user_knowledge_mastery;
DROP TABLE IF EXISTS interview_turns;
DROP TABLE IF EXISTS interview_sessions;
DROP TABLE IF EXISTS question_knowledge_points;
DROP TABLE IF EXISTS question_bank;
DROP TABLE IF EXISTS resumes;
DROP TABLE IF EXISTS rubrics;
DROP TABLE IF EXISTS knowledge_points;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;


-- -----------------------------------------------------------------------------
-- 1. users  用户
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT                COMMENT '用户主键',
    username         VARCHAR(64)     NOT NULL                                COMMENT '登录用户名',
    email            VARCHAR(128)    NOT NULL                                COMMENT '邮箱 (可作第二登录名)',
    password_hash    VARCHAR(128)    NOT NULL                                COMMENT 'BCrypt 密码散列',
    display_name     VARCHAR(64)                                             COMMENT '显示名',
    avatar_url       VARCHAR(512)                                            COMMENT '头像 URL',
    target_domain    VARCHAR(32)                                             COMMENT '求职方向: backend/frontend/ai/bigdata/cloudnative/...',
    experience_level VARCHAR(16)                                             COMMENT '经验等级: intern/junior/mid/senior',
    role             VARCHAR(16)     NOT NULL DEFAULT 'USER'                 COMMENT '角色: USER/ADMIN',
    active           TINYINT(1)      NOT NULL DEFAULT 1                      COMMENT '是否启用',
    last_login_at    DATETIME(3)                                             COMMENT '最后登录时间 (UTC)',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at       DATETIME(3)                                             COMMENT '软删除时间戳, 非 NULL 视为已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email    (email),
    KEY        idx_users_domain  (target_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';


-- -----------------------------------------------------------------------------
-- 2. knowledge_points  知识图谱 (标签体系)
-- -----------------------------------------------------------------------------
CREATE TABLE knowledge_points (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code           VARCHAR(128)    NOT NULL                                  COMMENT '语义 code, 例: backend.network.tcp.handshake',
    domain         VARCHAR(32)     NOT NULL                                  COMMENT '一级方向, 便于按方向批量筛选',
    name           VARCHAR(128)    NOT NULL                                  COMMENT '中文名',
    parent_id      BIGINT UNSIGNED                                           COMMENT '父节点 id, NULL 为顶层',
    description    TEXT                                                      COMMENT '知识点说明, 供 LLM prompt 使用',
    difficulty_avg DECIMAL(3,2)                                              COMMENT '全局平均难度 [1.00, 5.00]',
    weight         DECIMAL(3,2)    NOT NULL DEFAULT 1.00                     COMMENT '面试权重, 影响推荐优先级',
    embedding_ref  VARCHAR(128)                                              COMMENT '外部向量库引用 id',
    active         TINYINT(1)      NOT NULL DEFAULT 1,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_kp_code   (code),
    KEY        idx_kp_domain (domain, active),
    KEY        idx_kp_parent (parent_id),
    CONSTRAINT fk_kp_parent FOREIGN KEY (parent_id) REFERENCES knowledge_points(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识图谱 - 知识点树';


-- -----------------------------------------------------------------------------
-- 3. rubrics  评分模板 (用于开放题)
-- -----------------------------------------------------------------------------
-- dimensions 示例:
--   [
--     {"key":"star_completeness","name":"STAR完整度","weight":0.25,"scale_min":1,"scale_max":5},
--     {"key":"technical_depth","name":"技术深度","weight":0.30,"scale_min":1,"scale_max":5},
--     {"key":"problem_solving","name":"问题解决","weight":0.25,"scale_min":1,"scale_max":5},
--     {"key":"reflection","name":"反思能力","weight":0.10,"scale_min":1,"scale_max":5},
--     {"key":"quantification","name":"量化指标","weight":0.10,"scale_min":1,"scale_max":5}
--   ]
-- -----------------------------------------------------------------------------
CREATE TABLE rubrics (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64)     NOT NULL                                     COMMENT '模板 code, 例: project_star / system_design / algorithm',
    name        VARCHAR(128)    NOT NULL,
    description TEXT,
    dimensions  JSON            NOT NULL                                     COMMENT '维度定义数组',
    version     INT             NOT NULL DEFAULT 1,
    active      TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rubrics_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评分模板';


-- -----------------------------------------------------------------------------
-- 4. resumes  简历
-- -----------------------------------------------------------------------------
-- parsed_content JSON 推荐结构:
--   {
--     "basics":     {"name":"...","email":"...","phone":"...","target":"backend"},
--     "education":  [{"school":"...","degree":"...","period":"..."}],
--     "experiences":[{"company":"...","role":"...","period":"...","summary":"..."}],
--     "projects":   [{"name":"...","stack":["Java","Redis"],"role":"...","summary":"...","highlights":["..."]}],
--     "skills":     ["Java","Spring","MySQL",...]
--   }
-- mentioned_kps 存知识点 code 数组, 由 Python 解析时填充。
-- -----------------------------------------------------------------------------
CREATE TABLE resumes (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    original_filename VARCHAR(255),
    file_path         VARCHAR(512)    NOT NULL                               COMMENT '对象存储路径 (MinIO/S3/本地)',
    file_size_bytes   BIGINT,
    content_hash      CHAR(64)                                               COMMENT 'SHA-256(file), 去重与秒传判据',
    raw_text          LONGTEXT                                               COMMENT 'PDF 解析后的纯文本',
    parsed_content    JSON                                                   COMMENT '结构化简历 JSON',
    mentioned_kps     JSON                                                   COMMENT '简历中出现的知识点 code 列表',
    embedding_ref     VARCHAR(128)                                           COMMENT '外部向量库引用 id',
    analysis_status   VARCHAR(32)     NOT NULL DEFAULT 'PENDING'             COMMENT 'PENDING/PARSING/PARSED/FAILED',
    analysis_error    TEXT                                                   COMMENT '解析失败原因',
    is_active         TINYINT(1)      NOT NULL DEFAULT 1                     COMMENT '当前默认使用的简历 (同用户仅一份 active)',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at        DATETIME(3),
    PRIMARY KEY (id),
    KEY idx_resumes_user    (user_id, deleted_at),
    KEY idx_resumes_active  (user_id, is_active),
    KEY idx_resumes_status  (analysis_status),
    KEY idx_resumes_hash    (content_hash),
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户上传的简历';


-- -----------------------------------------------------------------------------
-- 5. question_bank  题目库
-- -----------------------------------------------------------------------------
-- reference_points (事实类题采分点):
--   [
--     {"point":"三次握手的目的:建立可靠连接,同步序列号","weight":0.2,"keywords":["可靠","ISN","序列号"]},
--     {"point":"SYN→SYN+ACK→ACK 报文流程","weight":0.3,"keywords":["SYN","ACK"]},
--     {"point":"为什么是三次不是两次:防止历史失效连接","weight":0.2},
--     {"point":"SYN 洪水与防御","weight":0.15,"keywords":["SYN flood","cookies"]}
--   ]
-- scoring_mode:
--   reference_points  - 纯靠采分点命中
--   rubric            - 纯靠 rubric_id 对应的多维度评分
--   hybrid            - 两者都用 (复杂题)
-- -----------------------------------------------------------------------------
CREATE TABLE question_bank (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type             VARCHAR(32)     NOT NULL                                COMMENT 'factual/project_based/algorithm/system_design/behavioral',
    domain           VARCHAR(32)     NOT NULL,
    difficulty       TINYINT         NOT NULL DEFAULT 3                      COMMENT '难度 [1,5]',
    stem             TEXT            NOT NULL                                COMMENT '题干',
    stem_hash        CHAR(64)        NOT NULL                                COMMENT 'SHA-256(normalize(stem)), 防重',
    standard_answer  TEXT                                                    COMMENT '参考答案 (事实类题可有)',
    reference_points JSON                                                    COMMENT '采分点数组',
    rubric_id        BIGINT UNSIGNED                                         COMMENT '开放题绑定的评分模板',
    scoring_mode     VARCHAR(16)     NOT NULL                                COMMENT 'reference_points/rubric/hybrid',
    meta             JSON                                                    COMMENT '{source,source_url,author,tags,...}',
    source           VARCHAR(32)     NOT NULL DEFAULT 'manual'               COMMENT 'manual/llm/crawled/leetcode (非枚举, 大小写随业务)',
    status           VARCHAR(16)     NOT NULL DEFAULT 'PENDING'              COMMENT 'PENDING/APPROVED/REJECTED',
    usage_count      INT             NOT NULL DEFAULT 0                      COMMENT '被抽中次数 (用于多样性控制)',
    avg_score        DECIMAL(4,2)                                            COMMENT '历史平均得分',
    embedding_ref    VARCHAR(128),
    created_by       BIGINT UNSIGNED                                         COMMENT '创建/审核者',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at       DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_qb_stem_hash   (stem_hash),
    KEY idx_qb_domain_status     (domain, status, deleted_at),
    KEY idx_qb_type              (type),
    KEY idx_qb_difficulty        (difficulty),
    KEY idx_qb_rubric            (rubric_id),
    CONSTRAINT fk_qb_rubric  FOREIGN KEY (rubric_id)  REFERENCES rubrics(id) ON DELETE SET NULL,
    CONSTRAINT fk_qb_creator FOREIGN KEY (created_by) REFERENCES users(id)   ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目库';


-- -----------------------------------------------------------------------------
-- 6. question_knowledge_points  题目 ↔ 知识点 多对多
-- -----------------------------------------------------------------------------
CREATE TABLE question_knowledge_points (
    question_id        BIGINT UNSIGNED NOT NULL,
    knowledge_point_id BIGINT UNSIGNED NOT NULL,
    weight             DECIMAL(3,2)    NOT NULL DEFAULT 1.00                 COMMENT '该题考察此 KP 的权重',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (question_id, knowledge_point_id),
    KEY idx_qkp_kp (knowledge_point_id),
    CONSTRAINT fk_qkp_q  FOREIGN KEY (question_id)        REFERENCES question_bank(id)    ON DELETE CASCADE,
    CONSTRAINT fk_qkp_kp FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目-知识点关联';


-- -----------------------------------------------------------------------------
-- 7. interview_sessions  面试会话
-- -----------------------------------------------------------------------------
-- config JSON 推荐结构:
--   {
--     "domain":"backend",
--     "sub_topics":["jvm","mysql","concurrency"],
--     "difficulty_range":[2,4],
--     "total_turns":10,
--     "scoring_mode":"standard",
--     "voice_enabled":false,
--     "include_algorithm":false,
--     "language":"zh-CN"
--   }
-- plan JSON: Planner Agent 输出的题目计划, 结构自定, 可含候选 question_id / kp_id。
-- -----------------------------------------------------------------------------
CREATE TABLE interview_sessions (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    resume_id        BIGINT UNSIGNED                                         COMMENT '本次面试关联的简历, 允许空(纯练习)',
    title            VARCHAR(255)                                            COMMENT '会话标题, 例如 "Java 后端-第3场"',
    status           VARCHAR(16)     NOT NULL DEFAULT 'PLANNING'             COMMENT 'PLANNING/IN_PROGRESS/COMPLETED/ABANDONED/FAILED',
    config           JSON            NOT NULL                                COMMENT '面试配置',
    plan             JSON                                                    COMMENT 'Planner 生成的题目计划',
    current_turn     INT             NOT NULL DEFAULT 0,
    total_turns      INT             NOT NULL DEFAULT 10,
    started_at       DATETIME(3),
    ended_at         DATETIME(3),
    duration_seconds INT                                                     COMMENT '面试实际用时 (秒)',
    version          INT             NOT NULL DEFAULT 0                      COMMENT '乐观锁, 防止并发提交',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_sessions_user   (user_id, created_at),
    KEY idx_sessions_status (status),
    KEY idx_sessions_resume (resume_id),
    CONSTRAINT fk_sessions_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_sessions_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试会话';


-- -----------------------------------------------------------------------------
-- 8. interview_turns  面试每一轮 Q/A
-- -----------------------------------------------------------------------------
-- score_detail 统一 schema (事实类 + 开放类 共用):
-- {
--   "scoring_type": "factual",         -- factual / open
--   "dimensions": {
--       "correctness": {"score":4,"reason":"..."},
--       "depth":       {"score":3,"reason":"..."},
--       "expression":  {"score":4,"reason":"..."}
--   },
--   "overall_score": 3.7,
--   "knowledge_points": [
--       {"kp_code":"backend.network.tcp.handshake","status":"hit"},
--       {"kp_code":"backend.network.tcp.syn_flood","status":"miss"}
--   ],
--   "feedback": {
--       "strengths":  ["..."],
--       "weaknesses": ["..."],
--       "suggested_followup_kps": ["backend.network.tcp.syn_flood"]
--   },
--   "rubric_scores": { ... }            -- 开放题才有
-- }
-- -----------------------------------------------------------------------------
CREATE TABLE interview_turns (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id          BIGINT UNSIGNED NOT NULL,
    turn_no             INT             NOT NULL                             COMMENT '第几轮, 从 1 开始',
    question_id         BIGINT UNSIGNED                                      COMMENT '来自题库时非空, LLM 即时生成为空',
    question_text       TEXT            NOT NULL,
    question_type       VARCHAR(32)     NOT NULL                             COMMENT '冗余 question_bank.type 便于查询',
    ask_audio_path      VARCHAR(512)                                         COMMENT '面试官 TTS 合成语音存储路径',

    user_answer_text    LONGTEXT                                             COMMENT 'ASR 结果或文本输入',
    user_audio_path     VARCHAR(512)                                         COMMENT '用户原始回答音频',
    asr_confidence      DECIMAL(4,3)                                         COMMENT 'ASR 平均置信度',

    score_overall       DECIMAL(4,2)                                         COMMENT '综合分 [0,5]',
    score_detail        JSON                                                 COMMENT '统一评分 schema, 见文件顶部注释',

    followup_of_turn_id BIGINT UNSIGNED                                      COMMENT '若本轮是追问, 指向被追问的原 turn',

    status              VARCHAR(16)     NOT NULL DEFAULT 'ASKED'             COMMENT 'ASKED/ANSWERED/SCORED/SKIPPED',
    asked_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    answered_at         DATETIME(3),
    scored_at           DATETIME(3),
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_turns_session_turn (session_id, turn_no),
    KEY idx_turns_question (question_id),
    KEY idx_turns_followup (followup_of_turn_id),
    KEY idx_turns_status   (status, scored_at),
    CONSTRAINT fk_turns_session  FOREIGN KEY (session_id)          REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_turns_question FOREIGN KEY (question_id)         REFERENCES question_bank(id)      ON DELETE SET NULL,
    CONSTRAINT fk_turns_followup FOREIGN KEY (followup_of_turn_id) REFERENCES interview_turns(id)    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试每一轮问答与评分';


-- -----------------------------------------------------------------------------
-- 9. user_knowledge_mastery  用户知识掌握度 (贝叶斯 + 间隔复习)
-- -----------------------------------------------------------------------------
-- 贝叶斯更新规则 (Beta 分布共轭先验, 由应用层执行):
--   先验:  Beta(alpha_0=1, beta_0=1)   等价于 Uniform(0,1)
--   观测:  单次答题对 KP 的 "命中证据" evidence ∈ [0,1]
--          - status=hit     => evidence = 1.0
--          - status=partial => evidence = 0.5
--          - status=miss    => evidence = 0.0
--   更新:  alpha += evidence * reliability
--          beta  += (1 - evidence) * reliability
--          (reliability 可由 asr_confidence / 题目权重 缩放, 取 [0.3, 1.0])
--   后验均值 mastery_mean = alpha / (alpha + beta)  (生成列)
--   不确定度  var = alpha*beta / ((alpha+beta)^2 * (alpha+beta+1))
-- 复习调度 (SM-2 简化版):
--   得分 >= 4 => review_interval_days *= 2
--   得分 <  2 => review_interval_days  = 1
--   next_review_at = now() + review_interval_days
-- -----------------------------------------------------------------------------
CREATE TABLE user_knowledge_mastery (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id              BIGINT UNSIGNED NOT NULL,
    knowledge_point_id   BIGINT UNSIGNED NOT NULL,
    alpha                DECIMAL(8,3)    NOT NULL DEFAULT 1.000              COMMENT 'Beta α: 累计"掌握"证据+先验',
    beta                 DECIMAL(8,3)    NOT NULL DEFAULT 1.000              COMMENT 'Beta β: 累计"未掌握"证据+先验',
    mastery_mean         DECIMAL(6,4)    GENERATED ALWAYS AS (alpha / (alpha + beta)) STORED COMMENT '后验均值, 作索引用',
    observations         INT             NOT NULL DEFAULT 0                  COMMENT '累计观测次数',
    last_score           DECIMAL(4,2)                                        COMMENT '最近一次作答涉及该 KP 的得分',
    last_practiced_at    DATETIME(3),
    next_review_at       DATETIME(3)                                         COMMENT '间隔复习算法给出的下次复习时间',
    review_interval_days INT             NOT NULL DEFAULT 1,
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ukm_user_kp    (user_id, knowledge_point_id),
    KEY idx_ukm_user_mastery     (user_id, mastery_mean),
    KEY idx_ukm_user_nextreview  (user_id, next_review_at),
    CONSTRAINT fk_ukm_user FOREIGN KEY (user_id)            REFERENCES users(id)            ON DELETE CASCADE,
    CONSTRAINT fk_ukm_kp   FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户知识点掌握度';


-- -----------------------------------------------------------------------------
-- 10. error_problems  错题本
-- -----------------------------------------------------------------------------
-- 约束说明:
--   uk_ep_user_question 仅在 question_id 非 NULL 时严格去重 (MySQL UNIQUE 允许多条 NULL)。
--   LLM 即时生成题 (question_id IS NULL) 可能产生多条同题记录, MVP 阶段接受该折中;
--   后续可通过 question_snapshot_hash 补强去重。
-- -----------------------------------------------------------------------------
CREATE TABLE error_problems (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    question_id       BIGINT UNSIGNED                                        COMMENT '来自题库时非空',
    question_snapshot TEXT                                                   COMMENT 'LLM 即时生成题时的题面快照',
    first_turn_id     BIGINT UNSIGNED                                        COMMENT '首次答错的 turn',
    latest_turn_id    BIGINT UNSIGNED                                        COMMENT '最近一次作答的 turn',
    attempt_count     INT             NOT NULL DEFAULT 1,
    last_score        DECIMAL(4,2),
    status            VARCHAR(16)     NOT NULL DEFAULT 'OPEN'                COMMENT 'OPEN/RESOLVED/MASTERED',
    resolved_at       DATETIME(3),
    first_seen_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ep_user_question (user_id, question_id),
    KEY idx_ep_user_status (user_id, status),
    CONSTRAINT fk_ep_user        FOREIGN KEY (user_id)        REFERENCES users(id)             ON DELETE CASCADE,
    CONSTRAINT fk_ep_question    FOREIGN KEY (question_id)    REFERENCES question_bank(id)     ON DELETE SET NULL,
    CONSTRAINT fk_ep_first_turn  FOREIGN KEY (first_turn_id)  REFERENCES interview_turns(id)   ON DELETE SET NULL,
    CONSTRAINT fk_ep_latest_turn FOREIGN KEY (latest_turn_id) REFERENCES interview_turns(id)   ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错题本';


-- -----------------------------------------------------------------------------
-- 11. evaluation_reports  整场面试评估报告 (含 Reflection 内容)
-- -----------------------------------------------------------------------------
-- 合并原设计中的 reflection_reports, 因两者本质 1:1 且字段高度重叠。
-- next_suggestions 示例:
--   [
--     {"type":"practice_kp","kp_code":"backend.jvm.gc","reason":"本场 2 次未命中"},
--     {"type":"next_interview","config":{"domain":"backend","sub_topics":["jvm"]}}
--   ]
-- -----------------------------------------------------------------------------
CREATE TABLE evaluation_reports (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id           BIGINT UNSIGNED NOT NULL,
    user_id              BIGINT UNSIGNED NOT NULL                            COMMENT '冗余便于按用户聚合',
    overall_score        DECIMAL(4,2),
    dimension_scores     JSON                                                COMMENT '{correctness,depth,expression,structure} 聚合',
    strengths            JSON                                                COMMENT '["..."] 优势项',
    weaknesses           JSON                                                COMMENT '["..."] 不足项',
    kp_summary           JSON                                                COMMENT '[{kp_code,hits,misses,partial,delta_mastery}]',
    professional_comment LONGTEXT                                            COMMENT '总评文字 (面试官视角)',
    improvement_plan     LONGTEXT                                            COMMENT 'Reflection: 改进计划',
    next_suggestions     JSON                                                COMMENT 'Reflection: 下一步建议',
    generated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_er_session (session_id),
    KEY idx_er_user_time (user_id, generated_at),
    CONSTRAINT fk_er_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_user    FOREIGN KEY (user_id)    REFERENCES users(id)              ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='整场面试评估报告';


-- -----------------------------------------------------------------------------
-- 12. learning_recommendations  个性化学习推荐
-- -----------------------------------------------------------------------------
CREATE TABLE learning_recommendations (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            BIGINT UNSIGNED NOT NULL,
    knowledge_point_id BIGINT UNSIGNED                                       COMMENT '针对哪个薄弱 KP',
    source_report_id   BIGINT UNSIGNED                                       COMMENT '产生此推荐的评估报告',
    resource_type      VARCHAR(32)     NOT NULL                              COMMENT 'article/video/doc/practice_question/tutorial',
    title              VARCHAR(255)    NOT NULL,
    resource_url       VARCHAR(1024),
    rationale          TEXT                                                  COMMENT 'LLM 生成的推荐理由',
    priority           TINYINT         NOT NULL DEFAULT 3                    COMMENT '1(高)-5(低)',
    estimated_minutes  INT,
    status             VARCHAR(16)     NOT NULL DEFAULT 'PENDING'            COMMENT 'PENDING/VIEWED/COMPLETED/DISMISSED',
    viewed_at          DATETIME(3),
    completed_at       DATETIME(3),
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_lr_user_status (user_id, status, priority),
    KEY idx_lr_kp          (knowledge_point_id),
    KEY idx_lr_report      (source_report_id),
    CONSTRAINT fk_lr_user   FOREIGN KEY (user_id)            REFERENCES users(id)              ON DELETE CASCADE,
    CONSTRAINT fk_lr_kp     FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id)   ON DELETE SET NULL,
    CONSTRAINT fk_lr_report FOREIGN KEY (source_report_id)   REFERENCES evaluation_reports(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个性化学习推荐';


-- -----------------------------------------------------------------------------
-- 13. async_jobs  异步任务 (简历解析 / 题库批量导入 / Embedding 生成)
-- -----------------------------------------------------------------------------
-- 设计目的:
--   - Java 接到请求创建 job, 立即返回 jobId
--   - Python Worker 轮询或订阅 (Redis Stream) 拉取并执行
--   - 前端轮询 GET /jobs/{id} 看进度
-- -----------------------------------------------------------------------------
CREATE TABLE async_jobs (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED,
    job_type            VARCHAR(32)     NOT NULL                             COMMENT 'resume_parse/embedding_gen/batch_import/...',
    related_entity_type VARCHAR(32)                                          COMMENT 'resume/question/user/...',
    related_entity_id   BIGINT UNSIGNED,
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING'           COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
    progress            TINYINT         NOT NULL DEFAULT 0                   COMMENT '0-100',
    payload             JSON                                                 COMMENT '任务入参',
    result              JSON                                                 COMMENT '任务结果',
    error_message       TEXT,
    attempt_count       INT             NOT NULL DEFAULT 0,
    max_attempts        INT             NOT NULL DEFAULT 3,
    scheduled_at        DATETIME(3),
    started_at          DATETIME(3),
    completed_at        DATETIME(3),
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_jobs_status (status, scheduled_at),
    KEY idx_jobs_user   (user_id, created_at),
    KEY idx_jobs_entity (related_entity_type, related_entity_id),
    CONSTRAINT fk_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步任务';
