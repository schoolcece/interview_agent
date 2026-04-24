# Entity / Repository 设计清单（B-0）

> 版本：v1.0（2026-04-23）
> 适用范围：Phase 0 重构 + Phase 1 MVP
> 上游依据：`docs/ARCHITECTURE.md`、`V1__init_schema.sql`
> 下游行动：作为 B-1（Entity/Repository 改造）的唯一参考

---

## 目录

- [0. 文档定位](#0-文档定位)
- [1. 总览：13 张表 → 哪些要建 Entity](#1-总览13-张表--哪些要建-entity)
- [2. 基础设施（BaseEntity / JSON / 枚举）](#2-基础设施baseentity--json--枚举)
- [3. Entity 逐表设计](#3-entity-逐表设计)
- [4. Repository 方法清单](#4-repository-方法清单)
- [5. 关键 JPA 决策](#5-关键-jpa-决策)
- [6. 落地顺序（给 B-1 用）](#6-落地顺序给-b-1-用)
- [7. 约定与禁忌](#7-约定与禁忌)

---

## 0. 文档定位

本文档回答一个问题：**"为了让 Phase 1 的 MVP 跑通，Java 侧需要哪些 Entity / Repository，每个字段怎么映射？"**

**不回答**的问题（放在后续文档）：
- Service / Controller 如何组织 → `SERVICE_DESIGN.md`（TODO，Phase 1 中段产出）
- API 端点 → `API.md`（TODO）
- Python 侧的 ORM / Pydantic model → `AGENT_DESIGN.md` 展开

---

## 1. 总览：13 张表 → 哪些要建 Entity

### 1.1 分档标准

| 档位 | 含义 | Java 侧处理 |
|---|---|---|
| **A. 必须（Phase 0-1 就要）** | Phase 1 MVP 闭环直接依赖 | 建 Entity + Repository + 可能的投影 DTO |
| **B. 只读必要（Phase 1）** | Phase 1 需要读、但写由别处（Admin/Python）管 | 建 Entity + Repository（只留查询方法） |
| **C. Phase 2+** | Phase 1 完全不触碰 | 不建，Phase 2 再补 |

### 1.2 分档结果

| # | 表名 | 档位 | 读者 | 写者 | 说明 |
|---|---|---|---|---|---|
| 1  | `users`                    | A | Java | Java | 认证中心，改造重点 |
| 2  | `resumes`                  | A | Java+Python | Java(元数据) + Python(解析结果) | 双写字段需谨慎隔离 |
| 3  | `interview_sessions`       | A | Java+Python | Java(CRUD + 状态流转) + Python(plan 写入) | 乐观锁 |
| 4  | `interview_turns`          | A | Java(读) | **Python 独写** | Java 侧 Entity 只读，禁止写 |
| 5  | `async_jobs`               | A | Java+Python | Java(创建) + Python(执行进度) | Phase 1 先简单，Phase 3 上 Redis Stream |
| 6  | `evaluation_reports`       | A | Java | Python(生成) | Java 只读即可；Phase 1 末期才写 |
| 7  | `user_knowledge_mastery`   | A | Java | **Java 独写**（消费评分事件） | 贝叶斯更新入口 |
| 8  | `error_problems`           | A | Java | **Java 独写**（消费评分事件） | 错题本更新入口 |
| 9  | `knowledge_points`         | B | Java+Python | Admin(Java) | Phase 1 只读；Phase 2 才有后台 CRUD |
| 10 | `question_bank`            | B | Java+Python | Admin(Java) | Phase 1 预置少量题即可 |
| 11 | `rubrics`                  | C | — | — | Phase 2 开放题评分才需要 |
| 12 | `question_knowledge_points`| C | — | — | Phase 2 与题库 CRUD 一起做 |
| 13 | `learning_recommendations` | C | — | — | Phase 3 才生成 |

### 1.3 Phase 1 Entity 列表（共 10 个）

```
A 档（7 个，完整 CRUD）
  User · Resume · InterviewSession · InterviewTurn(只读)
  AsyncJob · UserKnowledgeMastery · ErrorProblem
A 档（1 个，只读 + 末期生成）
  EvaluationReport
B 档（2 个，只读）
  KnowledgePoint · QuestionBank
```

Phase 1 **不建**的 3 个：`Rubric` / `QuestionKnowledgePoint` / `LearningRecommendation`。

---

## 2. 基础设施（BaseEntity / JSON / 枚举）

### 2.1 BaseEntity：统一审计字段

所有 Entity 继承 `BaseEntity`，消除每个 Entity 重复的 `created_at / updated_at`。

```java
// com.ai.interview.common.domain.BaseEntity
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

**要点**：
- 启用 `@EnableJpaAuditing`（在 `InterviewAgentSystemApplication` 或独立 `JpaConfig`）
- 时间用 `Instant`（UTC 语义清晰）而不是 `LocalDateTime`
- **不**继承 BaseEntity 的 Entity：`QuestionKnowledgePoint`（复合主键）

### 2.2 软删除基类

仅 `User / Resume / QuestionBank` 三张表软删除。提取 `SoftDeletableEntity extends BaseEntity`：

```java
@MappedSuperclass
@Getter @Setter
public abstract class SoftDeletableEntity extends BaseEntity {
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    public boolean isDeleted() { return deletedAt != null; }
}
```

不使用 Hibernate `@SQLDelete` + `@Where` 自动过滤，原因：
- `@Where` 在关联查询和原生 SQL 中行为易踩坑
- 本项目 `WHERE deleted_at IS NULL` 由 Repository 显式写，更可控

### 2.3 JSON 字段：统一用 `@JdbcTypeCode(SqlTypes.JSON)`

Hibernate 6（Spring Boot 3.2 带）原生支持。

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "json")
private Map<String, Object> config;          // 或专用 DTO 类（推荐）
```

**强烈建议**不要用 `Map<String,Object>`，而是为每个 JSON 字段定义 DTO：

| 字段 | 推荐 DTO 类 | 位置 |
|---|---|---|
| `interview_sessions.config` | `SessionConfig` | `entity.embeddable.SessionConfig` |
| `interview_sessions.plan` | `InterviewPlan` | `entity.embeddable.InterviewPlan` |
| `interview_turns.score_detail` | `ScoreDetail` | `entity.embeddable.ScoreDetail` |
| `resumes.parsed_content` | `ParsedResume` | `entity.embeddable.ParsedResume` |
| `resumes.mentioned_kps` | `List<String>` | 直接用 |
| `evaluation_reports.*` | 对应 DTO | `entity.embeddable.*` |
| `question_bank.reference_points` | `List<ReferencePoint>` | 同上 |
| `async_jobs.payload/result` | `Map<String, Object>` | 任务异构，保留 Map |

DTO 放在 `embeddable` 包作为"与持久化强绑定的值对象"，不要放到对外的 `dto` 包。

### 2.4 枚举类型

所有状态字段使用独立的 `enum` 而不是 String 字段：

```java
public enum SessionStatus { PLANNING, IN_PROGRESS, COMPLETED, ABANDONED, FAILED }
```

映射：`@Enumerated(EnumType.STRING) @Column(length = 16)`。

**枚举清单**（与 schema 对齐）：

| Entity | 枚举字段 | 值 |
|---|---|---|
| User | `role` | `USER`, `ADMIN` |
| Resume | `analysisStatus` | `PENDING`, `PARSING`, `PARSED`, `FAILED` |
| InterviewSession | `status` | `PLANNING`, `IN_PROGRESS`, `COMPLETED`, `ABANDONED`, `FAILED` |
| InterviewTurn | `status` | `ASKED`, `ANSWERED`, `SCORED`, `SKIPPED` |
| InterviewTurn | `questionType` | `FACTUAL`, `PROJECT_BASED`, `ALGORITHM`, `SYSTEM_DESIGN`, `BEHAVIORAL` |
| AsyncJob | `jobType` | `RESUME_PARSE`, `EMBEDDING_GEN`, `BATCH_IMPORT` |
| AsyncJob | `status` | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `CANCELED` |
| ErrorProblem | `status` | `OPEN`, `RESOLVED`, `MASTERED` |
| QuestionBank | `type` | `FACTUAL`, `PROJECT_BASED`, `ALGORITHM`, `SYSTEM_DESIGN`, `BEHAVIORAL` |
| QuestionBank | `status` | `PENDING`, `APPROVED`, `REJECTED` |
| QuestionBank | `scoringMode` | `REFERENCE_POINTS`, `RUBRIC`, `HYBRID` |

**枚举与 schema 一致性保证**：schema 用小写字符串，Entity 存储时统一写 **大写**（`@Enumerated(STRING)` 输出大写枚举名）。**V1 schema 的默认值需要对应改大写**，这是 B-1 需要附带的小改动。

> ⚠️ B-1 附带任务：把 `V1__init_schema.sql` 里的 `DEFAULT 'pending'`、`'open'`、`'manual'` 等小写字面量同步改成大写枚举名；或者 Java 侧用 `@Enumerated(EnumType.STRING)` + 自定义 `AttributeConverter<Enum, String>` 做大小写兼容。推荐前者，干净。

---

## 3. Entity 逐表设计

下表仅列 Phase 1 要用的字段映射关键点。完整注释放在 Java 源码。

### 3.1 `User`（A 档）

```
当前位置：com.ai.interview.entity.User（Phase 1 中段再迁到 user 包）
继承：SoftDeletableEntity
```

| 字段 | Java 类型 | 注解 / 备注 |
|---|---|---|
| username | String | `@Column(length=64, nullable=false, unique=true)` |
| email | String | `@Column(length=128, nullable=false, unique=true)` |
| passwordHash | String | `@Column(name="password_hash", length=128, nullable=false)` |
| displayName | String | `@Column(length=64)` |
| avatarUrl | String | `@Column(length=512)` |
| targetDomain | String | `@Column(length=32)` |
| experienceLevel | String | `@Column(length=16)` |
| role | `UserRole` enum | `@Enumerated(STRING) @Column(length=16, nullable=false)` |
| active | Boolean | `@Column(nullable=false)` |
| lastLoginAt | Instant | `@Column(name="last_login_at")` |

**对比旧实现要点**：
- 字段 `password` → `passwordHash`（字段名对齐 schema，明确语义）
- 删除 `firstName / lastName / phoneNumber`（schema 无）
- `role` 的 enum 定义从 `User` 内嵌移到独立文件 `com.ai.interview.user.entity.UserRole`

### 3.2 `Resume`（A 档）

```
包：com.ai.interview.resume.entity.Resume
继承：SoftDeletableEntity
```

| 字段 | Java 类型 | 注解 |
|---|---|---|
| userId | Long | `@Column(name="user_id", nullable=false)` |
| originalFilename | String | `@Column(length=255)` |
| filePath | String | `@Column(length=512, nullable=false)` |
| fileSizeBytes | Long | |
| contentHash | String | `@Column(length=64)` |
| rawText | String | `@JdbcTypeCode(LONGVARCHAR) @Column(columnDefinition="longtext")` |
| parsedContent | `ParsedResume` | `@JdbcTypeCode(JSON) @Column(columnDefinition="json")` |
| mentionedKps | `List<String>` | 同上 |
| embeddingRef | String | `@Column(length=128)` |
| analysisStatus | `AnalysisStatus` enum | `@Enumerated(STRING) @Column(length=32, nullable=false)` |
| analysisError | String | `@Column(columnDefinition="text")` |
| isActive | Boolean | `@Column(name="is_active", nullable=false)` |

**要点**：
- 删除旧字段 `parsedJsonPath`（schema 用 `parsedContent` 直接存 JSON）
- 删除旧枚举值 `PROCESSING_SKILLS / SKILLS_PROCESSED`（schema 不再有）
- 一个用户同时只允许一份 `isActive=true` 的简历 → **在 Service 层保证**（MySQL 无 partial unique index；若确需强约束可用触发器，Phase 1 不做）

### 3.3 `InterviewSession`（A 档）

```
包：com.ai.interview.interview.entity.InterviewSession
继承：BaseEntity（不软删除）
```

| 字段 | Java 类型 | 注解 |
|---|---|---|
| userId | Long | `nullable=false` |
| resumeId | Long | 可空 |
| title | String | `length=255` |
| status | `SessionStatus` enum | `@Enumerated(STRING) length=16 nullable=false` |
| config | `SessionConfig` | JSON `nullable=false` |
| plan | `InterviewPlan` | JSON 可空 |
| currentTurn | Integer | `nullable=false default=0` |
| totalTurns | Integer | `nullable=false default=10` |
| startedAt | Instant | |
| endedAt | Instant | |
| durationSeconds | Integer | |
| version | Integer | `@Version @Column(nullable=false)` **乐观锁关键** |

**要点**：
- 删除旧字段 `skillProfileId`（schema 无此概念，改用 `resumeId`）
- 删除旧字段 `planJsonPath`（改用 `plan` JSON 列）
- 删除旧字段 `interviewType`（合并进 `config.type`）
- 新增 `version` 字段 + `@Version`，与 schema `version INT NOT NULL DEFAULT 0` 对齐
- `startedAt`/`endedAt` 由 Service 在状态流转时显式写，不用 `@PrePersist`

### 3.4 `InterviewTurn`（A 档，**Java 侧只读**）

```
包：com.ai.interview.interview.entity.InterviewTurn
继承：BaseEntity
写权限：只有 Python 写；Java 侧 Repository 只暴露 find/count，禁止 save 方法
```

| 字段 | Java 类型 | 注解 |
|---|---|---|
| sessionId | Long | `nullable=false` |
| turnNo | Integer | `nullable=false` |
| questionId | Long | 可空（LLM 即时生成时） |
| questionText | String | `@Column(columnDefinition="text", nullable=false)` |
| questionType | `QuestionType` enum | `length=32 nullable=false` |
| askAudioPath | String | `length=512` |
| userAnswerText | String | `@JdbcTypeCode(LONGVARCHAR) columnDefinition="longtext"` |
| userAudioPath | String | `length=512` |
| asrConfidence | BigDecimal | `precision=4, scale=3` |
| scoreOverall | BigDecimal | `precision=4, scale=2` |
| scoreDetail | `ScoreDetail` | JSON |
| followupOfTurnId | Long | 可空 |
| status | `TurnStatus` enum | `length=16 nullable=false` |
| askedAt | Instant | `nullable=false` |
| answeredAt | Instant | |
| scoredAt | Instant | |

**禁忌**：
- **不要**在 `InterviewTurnRepository` 暴露 `save / saveAll / delete`。真要写，用 `@Modifying` 原生 SQL 并注释说明原因。
- **不要**用 `@OneToMany` 挂到 `InterviewSession` 上。保持 id 引用，Service 层显式 query。

### 3.5 `AsyncJob`（A 档）

```
包：com.ai.interview.job.entity.AsyncJob
继承：BaseEntity
```

| 字段 | Java 类型 | 注解 |
|---|---|---|
| userId | Long | 可空（系统任务） |
| jobType | `JobType` enum | `length=32 nullable=false` |
| relatedEntityType | String | `length=32`（值：`resume`/`question`/...） |
| relatedEntityId | Long | |
| status | `JobStatus` enum | `length=16 nullable=false` |
| progress | Integer | `nullable=false default=0` |
| payload | `Map<String, Object>` | JSON |
| result | `Map<String, Object>` | JSON |
| errorMessage | String | `columnDefinition="text"` |
| attemptCount | Integer | `nullable=false default=0` |
| maxAttempts | Integer | `nullable=false default=3` |
| scheduledAt | Instant | |
| startedAt | Instant | |
| completedAt | Instant | |

### 3.6 `UserKnowledgeMastery`（A 档）

```
包：com.ai.interview.mastery.entity.UserKnowledgeMastery
继承：BaseEntity
写权限：Java 独写（消费 turn.scored 事件）
```

| 字段 | Java 类型 | 注解 |
|---|---|---|
| userId | Long | `nullable=false` |
| knowledgePointId | Long | `nullable=false` |
| alpha | BigDecimal | `precision=8 scale=3 nullable=false` |
| beta | BigDecimal | `precision=8 scale=3 nullable=false` |
| masteryMean | BigDecimal | `insertable=false updatable=false` **生成列，只读** |
| observations | Integer | `nullable=false default=0` |
| lastScore | BigDecimal | `precision=4 scale=2` |
| lastPracticedAt | Instant | |
| nextReviewAt | Instant | |
| reviewIntervalDays | Integer | `nullable=false default=1` |

**要点**：
- `masteryMean` 是 MySQL **STORED 生成列**，Java 侧必须 `insertable=false, updatable=false`，否则 INSERT/UPDATE 会报错
- 复合唯一 `(user_id, knowledge_point_id)` 由 schema 保证；Service 层用 "先 find，存在则更新，不存在则 insert" 模式，**不**走 `MERGE INTO`

### 3.7 `ErrorProblem`（A 档）

```
包：com.ai.interview.mastery.entity.ErrorProblem
继承：BaseEntity（用 firstSeenAt 而非 createdAt，但 schema 用 first_seen_at; createdAt 留 null 会违反 schema。需要特殊处理）
```

**注意**：schema 里这张表没有 `created_at`，只有 `first_seen_at` 和 `updated_at`。因此：

- 不继承 `BaseEntity`，改为继承一个只含 `id + updatedAt` 的 mini 基类；或
- 直接独立建 Entity，自己管字段。**推荐后者**（只有这一张例外，单独处理清晰）。

字段：

| 字段 | 类型 | 备注 |
|---|---|---|
| userId | Long | 非空 |
| questionId | Long | 可空 |
| questionSnapshot | String | `columnDefinition="text"` |
| firstTurnId | Long | |
| latestTurnId | Long | |
| attemptCount | Integer | 非空，默认 1 |
| lastScore | BigDecimal | |
| status | `ErrorStatus` enum | |
| resolvedAt | Instant | |
| firstSeenAt | Instant | 非空 `@Column(name="first_seen_at", nullable=false, updatable=false)` |
| updatedAt | Instant | `@LastModifiedDate` |

### 3.8 `EvaluationReport`（A 档，只读 + 末期生成）

Phase 1 末期 Java 才需要读它（展示给用户）；写入由 Python 完成。

| 字段 | 类型 | 备注 |
|---|---|---|
| sessionId | Long | 非空，唯一 |
| userId | Long | 非空 |
| overallScore | BigDecimal | |
| dimensionScores | `Map<String, BigDecimal>` | JSON |
| strengths | `List<String>` | JSON |
| weaknesses | `List<String>` | JSON |
| kpSummary | `List<KpSummaryItem>` | JSON |
| professionalComment | String | `columnDefinition="longtext"` |
| improvementPlan | String | `columnDefinition="longtext"` |
| nextSuggestions | `List<NextSuggestion>` | JSON |
| generatedAt | Instant | |

继承：**不继承 BaseEntity**（schema 无 `created_at/updated_at`，只有 `generated_at`）。

### 3.9 `KnowledgePoint`（B 档，只读）

Phase 1 只读，不需要 set 方法。可暂用 `@Getter` 而不是 `@Data`。

字段按 schema 1:1 映射：`code, domain, name, parentId, description, difficultyAvg, weight, embeddingRef, active`。

### 3.10 `QuestionBank`（B 档，只读）

Phase 1 只读。复杂字段：
- `referencePoints` → `List<ReferencePoint>` JSON
- `meta` → `Map<String, Object>` JSON
- `stemHash`, `standardAnswer`, `scoringMode`, `usageCount`, `avgScore` 等直接映射

Phase 1 先不建 `@ManyToMany` 到 `KnowledgePoint`；Phase 2 做题库 CRUD 时再引入。

---

## 4. Repository 方法清单

仅列 Phase 1 **真正会被 Service 调用**的方法。其他一律不加（防止 API 爆炸）。

### 4.1 `UserRepository extends JpaRepository<User, Long>`

```java
Optional<User> findByUsernameAndDeletedAtIsNull(String username);
Optional<User> findByEmailAndDeletedAtIsNull(String email);
boolean existsByUsername(String username);
boolean existsByEmail(String email);
```

### 4.2 `ResumeRepository extends JpaRepository<Resume, Long>`

```java
List<Resume> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);
Optional<Resume> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
Optional<Resume> findByUserIdAndIsActiveTrueAndDeletedAtIsNull(Long userId);
```

### 4.3 `InterviewSessionRepository extends JpaRepository<InterviewSession, Long>`

```java
List<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);
long countByUserIdAndStatus(Long userId, SessionStatus status);
```

### 4.4 `InterviewTurnRepository extends JpaRepository<InterviewTurn, Long>`

⚠️ **只读。Service 层禁止调用 save/delete**（通过 Code Review 强制）。

```java
List<InterviewTurn> findBySessionIdOrderByTurnNoAsc(Long sessionId);
Optional<InterviewTurn> findBySessionIdAndTurnNo(Long sessionId, Integer turnNo);
long countBySessionIdAndStatus(Long sessionId, TurnStatus status);
```

### 4.5 `AsyncJobRepository extends JpaRepository<AsyncJob, Long>`

```java
Optional<AsyncJob> findByIdAndUserId(Long id, Long userId);
List<AsyncJob> findByUserIdAndJobTypeOrderByCreatedAtDesc(Long userId, JobType type);
```

### 4.6 `UserKnowledgeMasteryRepository extends JpaRepository<UserKnowledgeMastery, Long>`

```java
Optional<UserKnowledgeMastery> findByUserIdAndKnowledgePointId(Long userId, Long kpId);

@Query("""
  SELECT m FROM UserKnowledgeMastery m
  WHERE m.userId = :userId
  ORDER BY m.masteryMean ASC
""")
List<UserKnowledgeMastery> findWeakestPoints(Long userId, Pageable pageable);

List<UserKnowledgeMastery> findByUserIdAndNextReviewAtBefore(Long userId, Instant before);
```

### 4.7 `ErrorProblemRepository extends JpaRepository<ErrorProblem, Long>`

```java
Optional<ErrorProblem> findByUserIdAndQuestionId(Long userId, Long questionId);
Page<ErrorProblem> findByUserIdAndStatus(Long userId, ErrorStatus status, Pageable pageable);
```

### 4.8 `EvaluationReportRepository extends JpaRepository<EvaluationReport, Long>`

```java
Optional<EvaluationReport> findBySessionId(Long sessionId);
Page<EvaluationReport> findByUserIdOrderByGeneratedAtDesc(Long userId, Pageable pageable);
```

### 4.9 `KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long>`

```java
Optional<KnowledgePoint> findByCode(String code);
List<KnowledgePoint> findByDomainAndActiveTrue(String domain);
List<KnowledgePoint> findByCodeIn(Collection<String> codes);
```

### 4.10 `QuestionBankRepository extends JpaRepository<QuestionBank, Long>`

```java
Optional<QuestionBank> findByIdAndDeletedAtIsNull(Long id);
Page<QuestionBank> findByDomainAndStatusAndDeletedAtIsNull(
        String domain, QuestionStatus status, Pageable pageable);
```

---

## 5. 关键 JPA 决策

### ADR-E1：不使用 `@ManyToOne` / `@OneToMany` 关联对象，全部用 id 字段

**理由**：
- Python 会直写表，Hibernate 关联缓存和懒加载会和 Python 写入不一致
- Phase 1 根本不需要关联遍历，都是按 id 查询
- 避免 N+1、LazyInitializationException 这类 JPA 典型坑

**代价**：查询时要多写 `ResumeRepository.findByUserId(...)`，但换来代码可预测。

**例外**：Phase 2 如果题库 CRUD 页面需要展示题目-知识点关联，届时再决定是否给 `QuestionBank` 挂 `@ManyToMany`。

### ADR-E2：审计时间用 `Instant` 而不是 `LocalDateTime`

- `Instant` UTC 语义，和 schema `DATETIME(3)` 存 UTC 时间戳一致（`SET time_zone='+00:00'`）
- 展示时前端 / Controller 再按用户时区转

### ADR-E3：枚举映射到 `VARCHAR` 用大写

- `@Enumerated(STRING)` 输出大写枚举名
- V1 schema 的默认值需同步改大写（`'PENDING'` 而非 `'pending'`）→ **B-1 执行**

### ADR-E4：生成列 `mastery_mean` 仅读

```java
@Column(name = "mastery_mean", insertable = false, updatable = false,
        precision = 6, scale = 4)
private BigDecimal masteryMean;
```

忘加 `insertable=false` 会导致 `ERROR 3105 (HY000): The value specified for generated column 'mastery_mean' in table 'user_knowledge_mastery' is not allowed.`

### ADR-E5：包结构最终按"领域"组织（推迟到 Phase 1 中段）

**最终目标**（DDD 式领域分包）：
```
com.ai.interview
  ├── common        (BaseEntity, config, exception)
  ├── user          (User, UserRepository, AuthService, AuthController)
  ├── resume        (Resume, ...)
  ├── interview     (InterviewSession, InterviewTurn, ...)
  ├── mastery       (UserKnowledgeMastery, ErrorProblem, ...)
  ├── job           (AsyncJob, ...)
  ├── knowledge     (KnowledgePoint, ...)
  ├── question      (QuestionBank, ...)
  ├── evaluation    (EvaluationReport, ...)
  ├── agent         (AgentServiceClient, Python 交互)
  └── security      (JWT, UserContext, WebSocket)
```

**当前状态**：**保留扁平结构** `entity/ repository/ service/ controller/`。
**迁移时机**：Phase 1 写完第一轮端到端流程后做一次性批量迁移（有完整测试覆盖保护）。
**理由**：B-1 已经是主键类型 + JWT 结构 + 字段对齐的大重构，叠加包路径迁移会让 diff 太大，review 和回滚成本高。

### ADR-E6：Lombok 策略

- Entity：`@Getter @Setter @NoArgsConstructor` + 必要的 `@AllArgsConstructor`（JPA 必需 no-arg）
- 不用 `@Data`（toString 可能触发懒加载，equals 冲突主键语义）
- 不用 `@Builder`（Entity 应由 Service 工厂方法创建，Builder 容易漏设必填字段）

DTO 层可以用 `@Data / @Builder`，Entity 不要。

---

## 6. 落地顺序（给 B-1 用）

B-1 的目标是**项目恢复可编译 + Entity 层齐全**。保持扁平包结构不动。
每一步都**必须可编译**才能进下一步。

### Step 0：Schema 一致性修正

1. 把 schema 中剩余小写的枚举默认值统一改大写：
   - `error_problems.status DEFAULT 'open'` → `'OPEN'`
   - `question_bank.status DEFAULT 'pending'` → `'PENDING'`
   - `learning_recommendations.status DEFAULT 'pending'` → `'PENDING'`（Phase 2 才用到，一起改）
   - `question_bank.source DEFAULT 'manual'` 不是枚举，保留小写
2. 清空本地 MySQL 的 `interview_db` 库，让 Flyway 重新执行 V1（或在开发期直接 drop database 重建）

### Step 1：基础设施

1. 新建 `common/domain/BaseEntity.java`、`SoftDeletableEntity.java`
2. 新建 `common/config/JpaConfig.java`，`@EnableJpaAuditing`
3. 新建 `common/embeddable/`（放 SessionConfig、ScoreDetail 等 JSON DTO）
4. 新建 `common/enums/`（或分散放到各 Entity 同级）

### Step 2：User 改造

1. 重写 `entity/User.java`：继承 `SoftDeletableEntity`，`UUID`→`Long`，字段对齐 schema
2. 抽出 `entity/UserRole.java`
3. 更新 `repository/UserRepository.java`：`JpaRepository<User, Long>`
4. 更新 JWT 相关：`JwtTokenProvider` 删 `orgId`；`JwtAuthenticationFilter` 不再塞 orgId；`UserContext` 删 orgId；`LoginUserContextService.requireUserId()` 返回 `Long`，删 `requireOrgId` 与 `parseUuid`
5. 更新 `AuthService`：字段对齐 `passwordHash`，删 orgId 传参
6. 更新 `dto/RegisterRequest`：删 firstName/lastName，加 displayName
7. 更新 `AuthController`：删 `requireOrgId` 调用，toProfileResponse 字段对齐，交给全局异常处理器（去除 Controller try/catch）
8. 启动验证：`/api/auth/register` + `/api/auth/login`

### Step 3：Resume 改造

1. 重写 `entity/Resume.java`：继承 `SoftDeletableEntity`，`UUID`→`Long`，字段对齐
2. 抽出 `entity/AnalysisStatus.java`
3. 新建 `entity/embeddable/ParsedResume.java`（JSON 对象结构）
4. 更新 `repository/ResumeRepository.java`

### Step 4：InterviewSession 改造

1. 重写 `entity/InterviewSession.java`：字段按新 schema；加 `@Version`；加 `SessionConfig` / `InterviewPlan` JSON 字段
2. 抽出 `entity/SessionStatus.java`
3. 新建 `entity/embeddable/SessionConfig.java`、`InterviewPlan.java`
4. 更新 `repository/InterviewSessionRepository.java`
5. 更新 `InterviewService`：删 `requireOrgId`，参数 `skillProfileId`→`resumeId`，改用 SessionConfig/Plan 填 JSON
6. 更新 `dto/CreateInterviewSessionRequest`：字段 `skillProfileId`→`resumeId`（Long）+ 基础 config
7. 更新 `InterviewController`：路径从 `/interview` 保持不变（Phase 1 末期再 RESTful 化）
8. 更新 `InterviewWebSocketHandler` 的方法调用签名（如有必要）；鉴权修复留给 Phase 0 的 B-2

### Step 5：新增 Entity 骨架（只读 / 事件写入）

批量新建 Entity + Repository：
- `InterviewTurn` + `TurnStatus` + `QuestionType` + embeddable `ScoreDetail`
- `AsyncJob` + `JobType` + `JobStatus`
- `EvaluationReport` + embeddable `DimensionScores` / `KpSummaryItem` / `NextSuggestion`
- `UserKnowledgeMastery`
- `ErrorProblem` + `ErrorStatus`
- `KnowledgePoint`（只读）
- `QuestionBank` + `QuestionBankStatus` + `QuestionScoringMode` + embeddable `ReferencePoint`（只读）

Repository 方法只放 §4 列出的那些，**不要**加其他。

### Step 6：全局修补

1. `GlobalExceptionHandler` 增加 `Exception.class` 500 兜底 + `ObjectOptimisticLockingFailureException` 409
2. `InterviewAgentSystemApplication` 或 `JpaConfig` 启用 `@EnableJpaAuditing`

### Step 7：编译 + 启动验证

1. `mvn clean compile`
2. （需要 MySQL + Redis 在线）`mvn spring-boot:run`
3. 用 curl/Postman 验证 `/api/auth/register` + `/api/auth/login` + `/api/interview/session/create`

**合计预估**：2–3 人天（比原估 4.5 天低，因为不做包迁移）。

---

## 7. 约定与禁忌

**必须遵守**：
1. 所有 `userId` / `sessionId` / `resumeId` 等外键字段都是 `Long`，不是 `UUID`，不是 `Integer`
2. 所有时间字段是 `Instant`，不是 `LocalDateTime`
3. 所有 JSON 字段必须对应明确的 DTO 类（`Map<String,Object>` 仅 async_jobs 例外）
4. Repository 查询方法必须带软删除条件（`...AndDeletedAtIsNull` 或等价 JPQL）
5. `InterviewTurn` 只读（Java 侧不写）

**禁止**：
1. ❌ 在 Entity 上用 `@Data`
2. ❌ 在 Entity 上用 `@ManyToOne / @OneToMany / @ManyToMany`（Phase 1 范围内）
3. ❌ 用字符串拼接 SQL（全部走 JPQL 或 `@Query`）
4. ❌ 在 Controller 直接返回 Entity（必须过一层 DTO / VO）
5. ❌ 在 Service 层直接 `new` Entity（走工厂方法 `User.register(...)` / `InterviewSession.create(...)`，保证不变量）

---

**更新纪录**
- v1.0（2026-04-23）首版。与 `ARCHITECTURE.md v1.0` 对齐。
