# AI Interview Agent System —— Java 平台测试报告

- 项目：`com.ai:interview-agent-system:1.0.0`
- Spring Boot 版本：`3.2.0` / JDK 17 / Maven 3.9.8
- 测试执行时间：2026-04-23
- 测试执行人：本次自动化测试（CI 本地运行）
- 被测环境
  - Java 单元/接口测试：`mvn test`（MockMvc + Mockito，不依赖外部服务）
  - 真实接口冒烟测试：本地运行中的后端 `http://127.0.0.1:8080/api`，MySQL `10.101.171.175:3306/interview_db`，Redis `10.101.171.175:6379`

---

## 1. 测试范围

本次测试覆盖 Java 后端平台「当前已实现」的全部功能：

| 分类 | 模块 | 说明 |
| --- | --- | --- |
| 认证 | `AuthController` / `AuthService` / `JwtTokenProvider` | 注册 / 登录 / 获取个人信息 / JWT 签发与校验 |
| 安全上下文 | `LoginUserContextService` / `UserContext` / `JwtAuthenticationFilter` | 用户态解析、组织隔离、UUID 校验 |
| 健康检查 | `HealthController` | 服务存活探针 |
| 面试流程 | `InterviewController` / `InterviewService` | 会话创建 / 开始面试 / 提交回合 / 查询会话 / 触发评估 |
| 全局异常 | `GlobalExceptionHandler` | `AccessDenied` / `IllegalArgument` / `ResourceNotFound` 的统一响应 |

未被覆盖（当前源码尚未实现业务实体或主流程对外开放，故不纳入本轮测试）：
- `AgentServiceClient` 与 Python Agent 的真实集成（已在单测中以 Mock 形式替代）
- `InterviewWebSocketHandler`（WebSocket 实时通道）
- 简历 `Resume` 相关接口（仓库/实体已定义，暂无 Controller）

---

## 2. 测试方式

### 2.1 单元测试 / 接口切片测试（`mvn test`）

- 框架：JUnit 5、Mockito（`MockitoExtension`）、AssertJ、Spring Boot Test、`spring-security-test`
- Controller 层：`@WebMvcTest` + `MockMvc`，关闭 Security Filter（`@AutoConfigureMockMvc(addFilters = false)`），通过 `@MockBean` 注入服务层
- Service 层：`@ExtendWith(MockitoExtension.class)`，纯 Mock 注入 Repository / Client / Context
- 安全层：`ReflectionTestUtils` 注入 JWT 配置，基于 `ThreadLocal` 的 `UserContext` 验证

### 2.2 真实接口冒烟测试

- 工具：PowerShell `Invoke-WebRequest`
- 覆盖：注册 → 登录 → Profile → 面试会话 CRUD 边界，覆盖 2xx / 4xx 的关键分支
- 基础 URL：`http://127.0.0.1:8080/api`

---

## 3. 执行结果总览

### 3.1 Maven Surefire 汇总

```
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 通过 | 失败 | 耗时 |
| --- | ---: | ---: | ---: | ---: |
| `controller.AuthControllerApiTest` | 4 | 4 | 0 | 8.184 s |
| `controller.HealthControllerApiTest` | 1 | 1 | 0 | 0.400 s |
| `controller.InterviewControllerApiTest` | 5 | 5 | 0 | 0.423 s |
| `security.JwtTokenProviderTest` | 2 | 2 | 0 | 0.202 s |
| `security.LoginUserContextServiceTest` | 6 | 6 | 0 | 0.056 s |
| `service.AuthServiceTest` | 4 | 4 | 0 | 0.239 s |
| `service.InterviewServiceTest` | 5 | 5 | 0 | 0.153 s |
| **合计** | **27** | **27** | **0** | ≈ 9.66 s |

> 报告原件位于 `target/surefire-reports/`，包含每个测试类的 `TEST-*.xml` 详情。

### 3.2 真实接口冒烟汇总

| 编号 | 接口 | 场景 | 预期 | 实际 | 结果 |
| --- | --- | --- | :---: | :---: | :---: |
| A1 | `GET /api/health` | 服务存活 | 200 | 200 `{"status":"ok"}` | ✅ |
| A2 | `POST /api/auth/register` | 合法注册 | 200 | 200 返回 `accessToken/refreshToken/userId` | ✅ |
| A3 | `POST /api/auth/register` | 用户名重复 | 400 | 400 `{"error":"Username already exists"}` | ✅ |
| A4 | `POST /api/auth/login` | 正确密码 | 200 | 200 返回新 Token | ✅ |
| A5 | `POST /api/auth/login` | 错误密码 | 401 | 401 `{"error":"Invalid username or password"}` | ✅ |
| A6 | `GET /api/auth/profile` | 携带有效 JWT | 200 | 200 返回 Profile | ✅ |
| A7 | `GET /api/auth/profile` | 未携带 JWT | 401/403 | 403（Spring Security 默认入口点） | ✅ |
| I1 | `POST /api/interview/session/create` | 缺失字段 | 400 | 400 `{"error":"skillProfileId is required"}` | ✅ |
| I2 | `POST /api/interview/session/create` | 非法 UUID | 400 | 400 `{"error":"skillProfileId must be a valid UUID"}` | ✅ |
| I3 | `POST /api/interview/session/create` | 合法 UUID 但不存在 `skill_profiles` | 400/409（预期业务校验失败） | 403（详见 §5 缺陷记录） | ⚠️ |
| I4 | `GET /api/interview/{id}` | 非法 UUID | 400 | 400 `{"error":"sessionId must be a valid UUID"}` | ✅ |
| I5 | `GET /api/interview/{id}` | UUID 合法但会话不存在 | 404 | 404 `{"error":"Interview session not found"}` | ✅ |
| I6 | `POST /api/interview/{id}/start` | 会话不存在 | 404 | 404 `{"error":"Interview session not found"}` | ✅ |
| I7 | `POST /api/interview/{id}/evaluate` | 会话不存在 | 404 | 404 `{"error":"Interview session not found"}` | ✅ |

总计 14 项，通过 13 项，可观察到 1 项已知缺陷（I3）。

---

## 4. 各测试类细节

### 4.1 `AuthServiceTest`（Service 单测）

| 用例 | 验证点 |
| --- | --- |
| `login_shouldReturnTokens_whenCredentialsValid` | 账号存在 + 密码匹配 + 用户激活 → 返回 accessToken/refreshToken/username，`lastLoginAt` 被持久化 |
| `login_shouldThrow_whenPasswordInvalid` | 密码校验失败 → `IllegalArgumentException("Invalid username or password")` |
| `register_shouldCreateUser_whenInputValid` | 用户名/邮箱唯一 → 落库、默认角色 USER、返回 Token |
| `getUserById_shouldReturnEmpty_whenIdInvalid` | 非法 UUID 字符串 → 返回 `Optional.empty()` |

### 4.2 `InterviewServiceTest`（Service 单测）

| 用例 | 验证点 |
| --- | --- |
| `createInterviewSession_shouldCreatePlanningSession` | 初始状态 `PLANNING`，`currentTurn=0`，`totalTurns=15`，绑定当前 userId |
| `startInterview_shouldThrow_whenSessionNotFound` | 会话不存在 → `ResourceNotFoundException` |
| `startInterview_shouldMoveToInProgress` | 调 Agent Planning 成功 → 状态置 `IN_PROGRESS`，轮次自增 |
| `submitTurn_shouldCompleteSession_whenAgentReturnsComplete` | Agent 返回 `is_complete=true` → 状态 `COMPLETED`，`endedAt` 非空，轮次 +1 |
| `getSession_shouldThrow_whenSessionNotFound` | 会话不存在 → `ResourceNotFoundException` |

### 4.3 `JwtTokenProviderTest`（Security 单测）

| 用例 | 验证点 |
| --- | --- |
| `generateToken_shouldBeParsableAndValid` | HS512 签发 → `validateToken=true`，可解出 userId/email/orgId |
| `validateToken_shouldReturnFalse_whenTokenInvalid` | 非法字符串 → `false` |

### 4.4 `LoginUserContextServiceTest`（Security 单测）

| 用例 | 验证点 |
| --- | --- |
| `requireUserId_shouldThrow_whenContextMissing` | 无 `UserContext` → `AccessDeniedException("Unauthorized")` |
| `requireUserId_shouldReturnUuid_whenContextValid` | 设置后返回对应 UUID |
| `requireOrgId_shouldThrow_whenOrgMissing` | 无 orgId → `AccessDeniedException("Invalid organization context")` |
| `requireOrgId_shouldReturnOrgId_whenContextValid` | 返回 `tenant-1` |
| `parseUuid_shouldThrow_whenInvalid` | 非法字符串 → `IllegalArgumentException("sessionId must be a valid UUID")` |
| `parseUuid_shouldReturnUuid_whenValid` | 合法字符串 → 返回对应 UUID |

### 4.5 `AuthControllerApiTest`（Controller 切片测试）

| 用例 | 验证点 |
| --- | --- |
| `login_shouldReturn200` | POST `/auth/login` → 200，Body 含 `accessToken` |
| `register_shouldReturn400_whenServiceThrows` | Service 抛 `IllegalArgumentException` → 400（由 Controller 内 `catch` 捕获并返回） |
| `profile_shouldReturn200_whenUserExists` | 设置登录上下文 → 返回用户 Profile |
| `profile_shouldReturn403_whenUnauthenticated` | `requireUserId` 抛 `AccessDeniedException` → 经 `GlobalExceptionHandler` 转 403 |

### 4.6 `InterviewControllerApiTest`（Controller 切片测试）

| 用例 | 验证点 |
| --- | --- |
| `createSession_shouldReturn200` | POST `/interview/session/create` → 200，返回会话实体 |
| `startInterview_shouldReturn200` | POST `/interview/{id}/start` → 200，返回 Agent 响应体 |
| `submitTurn_shouldReturn200` | POST `/interview/{id}/turn` → 200，携带 `response/audioPath` |
| `getSession_shouldReturn404_whenSessionMissing` | 抛 `ResourceNotFoundException` → 404 |
| `evaluate_shouldReturn200` | POST `/interview/{id}/evaluate` → 200，返回 `overall_score` |

### 4.7 `HealthControllerApiTest`

| 用例 | 验证点 |
| --- | --- |
| `health_shouldReturnOk` | GET `/health` → 200 `{"status":"ok"}` |

---

## 5. 发现的缺陷 / 观察点

### BUG-001（等级：中）合法入参创建面试会话时返回 403

- 复现步骤：携带合法 JWT，`POST /api/interview/session/create`，`skillProfileId` 为格式正确但数据库中 `skill_profiles` 不存在的 UUID。
- 实际结果：HTTP 403，响应体为空。
- 根因分析：
  1. `InterviewService.createInterviewSession` 将 `skill_profile_id` 直接落库；
  2. 数据库存在外键约束 `fk_interview_sessions_skill_profile` → 触发 `DataIntegrityViolationException`；
  3. `GlobalExceptionHandler` 未处理该异常类型，异常冒泡；
  4. 容器派发到 `/error` 时 `UserContext` 与 `SecurityContext` 已在 `JwtAuthenticationFilter.finally` 中清空，`anyRequest().authenticated()` 命中，返回 403。
- 影响：状态码误导前端，且真实错误信息丢失。
- 建议修复：
  - 方案 A：在 `GlobalExceptionHandler` 增加 `DataIntegrityViolationException` → 400/409 映射；
  - 方案 B：在 `createInterviewSession` 中先校验 `skill_profile_id` 是否存在（推荐，避免依赖 DB 约束）。

### OBS-001 未认证访问受保护接口返回 403 而非 401

- 观察：未携带 Bearer Token 访问 `/auth/profile` 返回 403 且无响应体。
- 说明：这是 Spring Security 默认 `Http403ForbiddenEntryPoint` 的行为，符合当前配置但与 REST 规范（通常未认证用 401）略有出入。
- 建议：如需对外 API 规范化，可自定义 `AuthenticationEntryPoint` 返回 401 JSON 错误体。

### OBS-002 `InterviewController` 未捕获 Agent 调用失败

- `InterviewService.startInterview` 在 `AgentServiceClient` 抛异常时，会 **先** 将会话置为 `FAILED` 再抛 `RuntimeException`。Controller 未 `try-catch`，当前会导致 500（或与 BUG-001 相同的 403 链路）。
- 建议：补充 `RuntimeException` / `WebClientException` 的全局处理，或统一返回 `502 Bad Gateway`。

---

## 6. 覆盖的主要接口清单

| Method | Path（在 `context-path=/api` 下） | 认证 | 已覆盖用例 |
| :---: | --- | :---: | --- |
| GET | `/api/health` | 否 | `HealthControllerApiTest.health_shouldReturnOk` / A1 |
| POST | `/api/auth/register` | 否 | `AuthControllerApiTest.register_*` / A2, A3 |
| POST | `/api/auth/login` | 否 | `AuthControllerApiTest.login_*` / A4, A5 |
| GET | `/api/auth/profile` | 是 | `AuthControllerApiTest.profile_*` / A6, A7 |
| POST | `/api/interview/session/create` | 是 | `InterviewControllerApiTest.createSession_*` / I1, I2, I3 |
| POST | `/api/interview/{sessionId}/start` | 是 | `InterviewControllerApiTest.startInterview_*` / I6 |
| POST | `/api/interview/{sessionId}/turn` | 是 | `InterviewControllerApiTest.submitTurn_*` |
| GET | `/api/interview/{sessionId}` | 是 | `InterviewControllerApiTest.getSession_*` / I4, I5 |
| POST | `/api/interview/{sessionId}/evaluate` | 是 | `InterviewControllerApiTest.evaluate_*` / I7 |

---

## 7. 复现 / 重跑指南

### 7.1 本地执行 Maven 测试

```powershell
mvn '-Dsurefire.useFile=false' test
# 报告：target/surefire-reports/
```

如需 HTML 报告：

```powershell
mvn surefire-report:report
# 产物：target/reports/surefire.html（或 target/site/surefire-report.html）
```

### 7.2 本地执行真实接口冒烟

前置：后端已在 `http://127.0.0.1:8080/api` 启动，且 MySQL/Redis 可用。

```powershell
# 1) 健康检查
Invoke-WebRequest http://127.0.0.1:8080/api/health -UseBasicParsing

# 2) 注册
$body = @{ username="demo_user"; email="demo@test.com"; password="Passw0rd!";
           firstName="Demo"; lastName="User"; targetDomain="Backend"; experienceLevel="junior" } | ConvertTo-Json
$r = Invoke-WebRequest http://127.0.0.1:8080/api/auth/register -Method POST `
        -ContentType "application/json" -Body $body -UseBasicParsing
$token = ($r.Content | ConvertFrom-Json).accessToken

# 3) 查询个人信息
Invoke-WebRequest http://127.0.0.1:8080/api/auth/profile `
        -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
```

---

## 8. 结论

- Java 平台当前已实现的 **9 个 HTTP 接口** 与 **认证/会话/异常** 三条主线功能已被 **27 个自动化单测 + 14 项真实接口冒烟** 覆盖，全部关键路径表现符合预期。
- 存在 1 个中等级别缺陷（BUG-001），2 个可改进观察点（OBS-001 / OBS-002），建议纳入下一迭代修复。
- 建议后续工作
  1. 为 `DataIntegrityViolationException / RuntimeException / WebClientException` 补充全局异常处理；
  2. 引入 `@SpringBootTest + Testcontainers` 的端到端集成测试；
  3. 引入 `jacoco-maven-plugin` 统计行/分支覆盖率；
  4. 待 `Resume`、`InterviewWebSocketHandler` 对外暴露后，补齐对应测试用例。
