# Phase 0 重构学习笔记

> 本文档覆盖 Phase 0 **B-2（WebSocket 鉴权）** 和 **B-3（配置项安全化）** 的
> 设计原理、实现方案、关键代码解读、以及常见踩坑点。
> 适合在代码写完后边对照代码边阅读。

---

## 目录

- [1. B-2：WebSocket 鉴权](#1-b-2websocket-鉴权)
  - [1.1 问题分析](#11-问题分析)
  - [1.2 为什么不能直接用 Spring Security Filter](#12-为什么不能直接用-spring-security-filter)
  - [1.3 为什么 Token 放 query string 而不是 Header](#13-为什么-token-放-query-string-而不是-header)
  - [1.4 实现：HandshakeInterceptor 模式](#14-实现handshakeinterceptor-模式)
  - [1.5 session attributes 替代 ThreadLocal](#15-session-attributes-替代-threadlocal)
  - [1.6 SecurityConfig 为什么放行 /ws/**](#16-securityconfig-为什么放行-ws)
  - [1.7 对比：两步握手方案（未采用）](#17-对比两步握手方案未采用)
- [2. B-3：配置项安全化](#2-b-3配置项安全化)
  - [2.1 问题：配置文件里写死了什么](#21-问题配置文件里写死了什么)
  - [2.2 Spring Boot 环境变量解析规则](#22-spring-boot-环境变量解析规则)
  - [2.3 JWT_SECRET 为什么不给默认值](#23-jwt_secret-为什么不给默认值)
  - [2.4 .env 文件的工作原理](#24-env-文件的工作原理)
  - [2.5 docker-compose.yml 的改进点](#25-docker-composeyml-的改进点)
  - [2.6 CORS：从 @CrossOrigin("*") 迁移到全局配置](#26-cors从-crossorigin-迁移到全局配置)
- [3. 完整调用链梳理](#3-完整调用链梳理)
- [4. 踩坑笔记](#4-踩坑笔记)
- [5. Phase 0 收尾 Checklist](#5-phase-0-收尾-checklist)

---

## 1. B-2：WebSocket 鉴权

### 1.1 问题分析

旧 `InterviewWebSocketHandler` 的致命缺陷：

```java
// ❌ 旧代码：完全不鉴权
@Override
public void afterConnectionEstablished(WebSocketSession session) {
    sessionMap.put(session.getId(), session);
    // userId 是 null！
}
```

任何人不需要 token 就能建立 WebSocket 连接，然后：
1. 调用 `interviewService.startInterview(sessionId)`
2. `loginUserContextService.requireUserId()` 从 `UserContext` 取 userId
3. **ThreadLocal 是空的** → 抛 `AccessDeniedException`（理论上正确，但报错不友好，且握手阶段已经浪费了资源）

---

### 1.2 为什么不能直接用 Spring Security Filter

Spring Security 的 `JwtAuthenticationFilter` 是标准 Servlet Filter，它在 HTTP 请求的处理链上运行。

WebSocket 连接的生命周期：
```
HTTP Upgrade 请求 (一次性)
        │
        ▼
[Servlet Filter Chain 运行]  ← JwtAuthenticationFilter 在这里写 ThreadLocal
        │
        ▼
[WebSocket Handshake]  ← Spring WS HandshakeInterceptor 在这里
        │
        ▼
[持久化 TCP 连接建立]  ← HTTP 请求生命周期结束，ThreadLocal 被清理
        │
        ▼
后续 WS 消息 ──► afterConnectionEstablished / handleTextMessage
                  ← 这时已经没有 HTTP 请求了，ThreadLocal 是空的
```

关键结论：
- **ThreadLocal 的生命周期和 HTTP 请求绑定**，WS 消息处理运行在不同线程，ThreadLocal 一定是空的
- 所以必须在握手时把认证信息存到 **`WebSocketSession.getAttributes()`**，它和 WS session 绑定，整个会话期间有效

---

### 1.3 为什么 Token 放 query string 而不是 Header

浏览器原生 WebSocket API **不支持自定义请求头**：

```javascript
// ❌ 不能这样：
const ws = new WebSocket('ws://host/ws/interview', {
    headers: { Authorization: 'Bearer ...' }  // 浏览器 WebSocket 忽略此参数
});

// ✅ 只能这样：
const ws = new WebSocket('ws://host/ws/interview?token=eyJhb...');
```

**安全注意事项**：token 出现在 URL 中 → 会记录在服务器 access log：
- 缓解方案 1：Access token 有效期短（本项目 24h）
- 缓解方案 2：生产日志配置过滤 `token=` 参数
- 缓解方案 3（Phase 5）：连接后第一条消息做二次鉴权（两步握手），握手完成后丢弃 URL token

---

### 1.4 实现：HandshakeInterceptor 模式

```
[前端] ws://host/ws/interview?token=eyJhb...
                │
                ▼
    JwtHandshakeInterceptor.beforeHandshake()
        1. 从 request.getURI() 解析 query param "token"
        2. JwtTokenProvider.validateToken(token) → 签名+过期校验
        3. JwtTokenProvider.getUserIdFromJWT(token) → 拿 userId
        4. attributes.put("userId", userId) → 存入 WS session
        5. return true → 允许握手继续
           return false → 拒绝（HTTP 401）
                │
                ▼
    WebSocket 连接建立
                │
                ▼
    InterviewWebSocketHandler.handleTextMessage()
        Long userId = (Long) session.getAttributes().get("userId")
        UserContext.setUserId(userId)  // 临时注入 ThreadLocal
        try {
            service.doSomething();     // Service 层 requireUserId() 能取到
        } finally {
            UserContext.clear();
        }
```

核心代码（`JwtHandshakeInterceptor.java`）：

```java
@Override
public boolean beforeHandshake(ServerHttpRequest request, ...,
                                Map<String, Object> attributes) {
    String token = extractToken(request);        // 从 query string 取 token
    if (token == null || !tokenProvider.validateToken(token)) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;                            // 中断握手
    }
    Long userId = tokenProvider.getUserIdFromJWT(token);
    attributes.put(USER_ID_ATTR, userId);        // 存入 session attributes
    return true;                                 // 允许握手继续
}
```

---

### 1.5 session attributes 替代 ThreadLocal

| | ThreadLocal | WebSocketSession.getAttributes() |
|---|---|---|
| 生命周期 | HTTP 请求 | WebSocket session（整个连接期间） |
| 线程安全 | 每线程独立 | session 级别（单连接不并发） |
| 在 WS 消息处理中是否有效 | ❌ 空的 | ✅ 有效 |
| 写入时机 | Filter（HTTP 请求开始） | HandshakeInterceptor（握手时） |

在 `handleTextMessage` 里要调用 `Service` 层时，Service 依赖 `UserContext`（ThreadLocal）。因此需要在消息处理入口处手动注入再清理：

```java
UserContext.setUserId(userId);
try {
    service.xxx();
} finally {
    UserContext.clear();   // ⚠️ 必须清理！否则线程复用时会串
}
```

---

### 1.6 SecurityConfig 为什么放行 /ws/**

`SecurityConfig` 里：
```java
.requestMatchers("/ws/**").permitAll()
```

**理由**：SockJS 的握手是 HTTP 请求，如果 Spring Security 在握手路径上要求鉴权：
1. Spring Security 的 JWT Filter 会运行，但 `UserContext` 只有到 filter 完成后才有值
2. `JwtHandshakeInterceptor` 在 Security Filter Chain **之后**运行，时序上没问题
3. 但如果 Security 直接拦截（403/401），握手请求根本到不了 Interceptor

放行 `/ws/**` 的含义是："让这个路径绕过 HTTP 层的认证要求，交给 WS 握手层的 Interceptor 自己处理"。这不是"允许匿名访问"，Interceptor 仍然会拒绝没有合法 token 的连接。

---

### 1.7 对比：两步握手方案（未采用）

另一种方案：连接建立后，客户端立即发送一条 `{type: "auth", token: "..."}` 消息；服务器收到后验证，失败则关闭连接。

| | query string token（当前方案） | 两步握手 |
|---|---|---|
| 实现复杂度 | 低 | 中（需要处理鉴权期间的并发消息） |
| Token 暴露 | URL / access log | 消息体（相对安全） |
| 握手失败响应 | HTTP 401（标准） | 需要自定义关闭码 |
| 浏览器兼容性 | 高 | 高 |

Phase 0 选择 query string 方案：实现简单、够用。Phase 5 如需更严格可换两步握手。

---

## 2. B-3：配置项安全化

### 2.1 问题：配置文件里写死了什么

旧 `application.yml` 的问题：

| 配置项 | 旧写法 | 问题 |
|---|---|---|
| `jwt.secret` | 明文字符串 | 密钥泄漏 → 任何人可伪造 JWT |
| 数据库 IP | `10.101.171.175` | 内网 IP 硬编码，换环境就要改代码 |
| 数据库密码 | `interview` | 密码明文 → git log 里永远留着 |
| Redis IP | 同上 | 同上 |
| Agent URL | 同上 | 换部署环境需要重新编译 |

**安全原则**：凡是"换环境需要改的值"或"不能泄漏的值"都不能硬编码在配置文件里。

---

### 2.2 Spring Boot 环境变量解析规则

Spring Boot 的 `${VAR_NAME:default}` 语法：

```yaml
# 格式：${环境变量名:默认值}
db.host: ${DB_HOST:localhost}    # 如果 DB_HOST 未设置，用 localhost
jwt.secret: ${JWT_SECRET}        # 无默认值：启动时如果未设置则报错
```

Spring Boot 读取环境变量的优先级（高到低）：
1. 命令行参数：`--jwt.secret=xxx`
2. 系统环境变量：`export JWT_SECRET=xxx`（或 `.env` 加载后）
3. `application-{profile}.yml`（如 `application-dev.yml`）
4. `application.yml` 中的默认值

**命名规则**：环境变量 `DB_HOST` → Spring 属性 `db.host`（下划线转点、大写转小写）。这是 Spring Relaxed Binding 特性。

---

### 2.3 JWT_SECRET 为什么不给默认值

```yaml
# ✅ 正确：强制设置，没有默认值
jwt:
  secret: ${JWT_SECRET}

# ❌ 错误：给了"安全"默认值，实际上是假安全
jwt:
  secret: ${JWT_SECRET:please-change-this-in-production}
```

原因：如果有默认值，开发者忘记设置环境变量时，应用仍能启动，但用了一个公开的弱密钥。任何人知道这个默认值就能伪造 JWT。

**没有默认值 → 启动失败 → 开发者必须显式处理**。这叫 **Fail-Fast**（快速失败）原则：让错误在启动时暴露，而不是在运行时悄悄产生安全漏洞。

生成安全密钥的命令：
```bash
# 方法 1：Python（推荐）
python3 -c "import secrets; print(secrets.token_hex(64))"

# 方法 2：openssl
openssl rand -hex 64

# 方法 3：Java（在 JShell 里）
new java.math.BigInteger(512, new java.security.SecureRandom()).toString(16)
```

---

### 2.4 .env 文件的工作原理

```
项目根目录
├── .env.example    ← git 追踪：模板，不含真实密钥
├── .env            ← .gitignore 忽略：本地真实配置
└── backend/
    └── src/main/resources/application.yml
```

**`.env` 文件是什么**：一行一个 `KEY=VALUE` 的纯文本文件。

**Spring Boot 怎么读它**：Spring Boot 本身**不会自动读 `.env` 文件**。需要通过其中一种方式注入：

1. **IDE 方式**（IntelliJ）：Run Configuration → Environment variables → 手动填（或用 `.env` 插件自动加载）
2. **Maven 方式**：
   ```bash
   # PowerShell：先 source .env，再启动
   Get-Content .env | ForEach-Object {
       if ($_ -match '^([^=]+)=(.*)$') { [System.Environment]::SetEnvironmentVariable($Matches[1], $Matches[2]) }
   }
   mvn spring-boot:run
   ```
3. **docker-compose 方式**：`docker-compose.yml` 里的 `env_file: .env` 或 `${VAR:-default}` 自动从 `.env` 读取（Docker Compose 原生支持）

**黄金法则**：
- `.env.example` → **要**提交到 git（告诉别人需要哪些变量）
- `.env` → **永远不**提交到 git

---

### 2.5 docker-compose.yml 的改进点

| 项目 | 旧版 | 新版 |
|---|---|---|
| MySQL 版本 | `mysql:8` | `mysql:8.0`（锁定小版本） |
| Redis 版本 | `redis:6` | `redis:7-alpine`（升到 Redis 7，用轻量 alpine 镜像） |
| 密码来源 | 硬编码 | `${DB_PASSWORD:-interview_dev_password}`（从 `.env` 读） |
| Milvus 启用 | 默认启动（Phase 1 用不到） | `profiles: [vector]`，按需启动 |
| Milvus 版本 | `v2.3.5` | `v2.4.0` |
| phpMyAdmin | 默认启动 | `profiles: [tools]`，按需启动 |
| 文件位置 | `backend/docker-compose.yml` | 项目根目录 `docker-compose.yml` |

**Docker Compose Profiles** 是什么：

```yaml
services:
  phpmyadmin:
    profiles: [tools]    # 只有 --profile tools 才启动
  milvus:
    profiles: [vector]   # 只有 --profile vector 才启动
```

这样 `docker compose up -d` 默认只启 MySQL + Redis + MinIO（Phase 1 需要的），不会浪费资源跑 Milvus。

**Phase 1 推荐启动方式**：
```bash
# 复制并编辑环境变量
cp .env.example .env
# 编辑 .env，至少设置 JWT_SECRET

# 只启 Phase 1 依赖
docker compose up -d mysql redis minio

# 等待健康检查通过后，启动 Java
cd backend
mvn spring-boot:run
```

---

### 2.6 CORS：从 @CrossOrigin("*") 迁移到全局配置

**旧做法**（错误）：
```java
@CrossOrigin(origins = "*")  // ❌ 每个 Controller 单独加，且允许所有来源
@RestController
public class AuthController { ... }
```

**新做法**（正确）：在 `SecurityConfig` 里集中配置：

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("${cors.allowed-origins}"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);  // 允许携带 Cookie / Authorization
    ...
}
```

**为什么不能用 `setAllowedOrigins("*")` + `setAllowCredentials(true)`**：

浏览器安全策略禁止这种组合：当请求携带凭证（Cookie / Authorization Header）时，服务器不能返回 `Access-Control-Allow-Origin: *`（必须是具体域名）。否则浏览器会拦截响应。

正确做法：用 `setAllowedOriginPatterns()`，它支持通配符（如 `http://localhost:*`）且与凭证兼容。

---

## 3. 完整调用链梳理

### WebSocket 鉴权完整流程

```
前端
  ├── 先 POST /api/auth/login → 拿到 accessToken
  └── new WebSocket('ws://localhost:8080/api/ws/interview?token=' + accessToken)
              │
              ▼
    [Servlet Container HTTP Upgrade Request]
              │
              ▼
    [Spring Security FilterChain]
      requestMatchers("/ws/**").permitAll()
      → 不走 JwtAuthenticationFilter，直接放行
              │
              ▼
    [JwtHandshakeInterceptor.beforeHandshake()]
      1. 解析 query param: token = "eyJhbGci..."
      2. jwtTokenProvider.validateToken(token) → 验签+过期
      3. jwtTokenProvider.getUserIdFromJWT(token) → userId = 42
      4. attributes.put("userId", 42L)
      5. return true
              │
              ▼
    [WebSocket 连接建立]
              │
              ▼ 消息到达
    [InterviewWebSocketHandler.handleTextMessage()]
      1. getUserId(session) → 42L (从 attributes 取)
      2. UserContext.setUserId(42L)
      3. try { interviewService.startInterview(sessionId) }
           └── requireUserId() → 42L ✅
         finally { UserContext.clear() }
```

### 配置项加载完整流程

```
应用启动
  │
  ├── [开发环境] IDE 从 Run Config / .env 文件注入系统环境变量
  │     JWT_SECRET=abc123...
  │     DB_HOST=localhost
  │
  ├── [生产环境] systemd / k8s secret → 系统环境变量
  │
  ▼
Spring Boot ApplicationContext 初始化
  │
  ├── 读取 application.yml
  │     jwt.secret: ${JWT_SECRET}  → 从系统环境变量取 → abc123...
  │     spring.datasource.host: ${DB_HOST:localhost} → localhost
  │
  └── 如果 JWT_SECRET 未设置 → 启动失败（Fail-Fast）✅
```

---

## 4. 踩坑笔记

### 坑 1：WebSocket Handler 里 ThreadLocal 是空的

**现象**：`loginUserContextService.requireUserId()` 抛 `AccessDeniedException`。

**原因**：WS 消息处理线程和 HTTP 请求线程不是同一个，ThreadLocal 为空。

**解法**：如上文所述，从 `session.getAttributes()` 取 userId，再手动写入 ThreadLocal，用完立刻清理。

---

### 坑 2：`setAllowedOrigins("*")` + `setAllowCredentials(true)` 报错

**现象**：浏览器报 `Access to fetch at 'http://...' has been blocked by CORS policy`。

**原因**：W3C CORS 规范明确禁止这种组合。

**解法**：改用 `setAllowedOriginPatterns(List.of("http://localhost:*"))`，或指定具体来源。

---

### 坑 3：WS 握手路径没放行，Interceptor 根本进不去

**现象**：握手返回 403，`JwtHandshakeInterceptor` 没有日志。

**原因**：Spring Security 先于 HandshakeInterceptor 运行。`/ws/**` 没有放行，Security 直接返回 403。

**解法**：`SecurityConfig` 中对 `/ws/**` 加 `.permitAll()`（鉴权交给 Interceptor）。

---

### 坑 4：`${JWT_SECRET}` 没有默认值，IDEA 启动报错

**现象**：`Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"`。

**原因**：没有在 Run Configuration 里设置环境变量。

**解法**：IntelliJ → Edit Configurations → Environment variables → `JWT_SECRET=<你生成的密钥>`。

---

### 坑 5：SockJS 端点不匹配

**现象**：客户端连接 `/api/ws/interview` 成功，但 SockJS 握手 404。

**原因**：SockJS 会在路径下生成多个端点（如 `/ws/interview/info`），注册时路径要精确。

**解法**：`registry.addHandler(handler, "/ws/interview")`，加上 `.withSockJS()` 后 SockJS 会自动处理子路径。

---

### 坑 6：docker-compose 读不到 `.env` 里的变量

**现象**：`${DB_PASSWORD}` 没有被替换。

**原因**：`.env` 文件和 `docker-compose.yml` 不在同一目录。

**解法**：把 `docker-compose.yml` 和 `.env` 都放在项目根目录；或在命令行指定：`docker compose --env-file path/to/.env up`。

---

## 5. Phase 0 收尾 Checklist

所有打勾项都完成后，Phase 0 结束，进入 Phase 1 文本 MVP 闭环。

**Schema & 数据模型**
- [x] V1__init_schema.sql 落地（13 张表，枚举默认值大写）
- [x] Flyway 迁移可正常执行

**文档**
- [x] ARCHITECTURE.md（含 3 张 Mermaid 图）
- [x] AGENT_DESIGN.md
- [x] ENTITY_PLAN.md（B-0 Entity 设计清单）
- [x] PHASE0_GUIDE.md（本文档）
- [x] 旧文档归档到 docs/legacy/

**Java 代码 - Entity & Repository（B-1）**
- [x] BaseEntity / SoftDeletableEntity
- [x] JpaConfig（@EnableJpaAuditing）
- [x] 10 个 Entity 骨架（字段类型 Long，枚举对齐 schema）
- [x] 10 个 Repository（方法清单精简）
- [x] `mastery_mean` 生成列 `insertable=false, updatable=false`

**Java 代码 - JWT & Security（B-1 + B-2）**
- [x] JWT 去除 org_id claim
- [x] UserContext / LoginUserContextService 返回 Long
- [x] JwtHandshakeInterceptor（WS 握手鉴权）
- [x] SecurityConfig 放行 /ws/**，集中配置 CORS
- [x] InterviewWebSocketHandler 从 session attributes 读 userId
- [x] GlobalExceptionHandler 覆盖 5 种异常（401/403/400/404/409/500）
- [x] Controller 层去除 try/catch 和 @CrossOrigin("*")

**配置安全化（B-3）**
- [x] application.yml 所有硬编码项改环境变量
- [x] JWT_SECRET 无默认值（Fail-Fast）
- [x] .env.example 提交到 git
- [x] .gitignore 覆盖 .env
- [x] docker-compose.yml 移至项目根，密码读环境变量，Milvus/phpMyAdmin 按需 profile 启动

**验证**
- [x] mvn clean compile 通过
- [ ] 本地启动（需要先 `docker compose up -d mysql redis minio` + 设置 `.env`）
- [ ] `/api/auth/register` + `/api/auth/login` curl 验证
- [ ] WS 握手：不带 token → 401；带合法 token → 握手成功

---

**Phase 1 入口**：见 `ARCHITECTURE.md` §10 Phase 1 任务列表。优先级：Resume 上传 + Python Worker 接入。
