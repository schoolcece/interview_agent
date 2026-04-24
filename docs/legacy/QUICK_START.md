# Spring Boot 后端快速入门指南

## 项目概述

这是一套完整的 Spring Boot 3.2 后端框架，为多用户 AI 面试系统提供支持。该框架与 Python FastAPI Agent 服务配合，实现完整的面试模拟、评估和学习推荐功能。

## 前置条件

- **Java 17+** (JDK 17 或更高版本)
- **Maven 3.9+**
- **Docker & Docker Compose** (用于快速启动数据库)
- **PostgreSQL 15+**
- **Redis 7+**
- **Git**

## 快速开始 (5分钟)

### 1. 启动基础设施

```bash
# 启动 PostgreSQL, Redis, Milvus, MinIO
docker-compose up -d

# 查看服务运行状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

成功启动后，你将获得：
- **PostgreSQL**: `localhost:5432` (user: postgres, password: postgres)
- **Redis**: `localhost:6379`
- **Milvus**: `localhost:19530` (向量数据库)
- **MinIO**: `http://localhost:9000` 和 `http://localhost:9001` (对象存储)
- **pgAdmin**: `http://localhost:5050` (数据库管理工具)

### 2. 编译项目

```bash
# 清理并编译
mvn clean compile

# 下载依赖并编译 (如果是第一次)
mvn clean package
```

### 3. 运行应用

**方式 1：通过 Maven 直接运行**
```bash
mvn spring-boot:run
```

**方式 2：编译后运行 JAR**
```bash
mvn clean package -DskipTests
java -jar target/interview-agent-system-1.0.0.jar
```

**方式 3：使用 IDE 运行**
- 在 IDE 中打开项目
- 找到 `InterviewAgentSystemApplication.java`
- 右键 → Run

### 4. 验证应用启动

```bash
# 检查健康状态
curl http://localhost:8080/api/health

# 预期输出: {"status":"ok"}
```

## 项目结构说明

```
src/main/java/com/ai/interview/
├── config/               # Spring 配置 (Security, WebSocket, etc.)
├── entity/               # JPA 实体 (数据库表映射)
├── repository/           # 数据访问层 (Spring Data JPA)
├── service/              # 业务逻辑层
├── controller/           # REST API 端点
├── websocket/            # WebSocket 处理
├── client/               # 外部服务调用 (Python Agent)
├── dto/                  # 数据传输对象
├── exception/            # 异常处理
├── util/                 # 工具函数
└── aspect/               # AOP 切面 (日志、性能、安全)
```

## 核心功能实现

### 1. JWT 认证与用户隔离

**用户注册**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "secure123",
    "firstName": "Alice",
    "lastName": "Johnson",
    "targetDomain": "Backend",
    "experienceLevel": "intermediate"
  }'
```

**用户登录**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "secure123"
  }'
```

预期响应：
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "alice@example.com",
  "username": "alice",
  "expiresIn": 86400000
}
```

### 2. 多用户数据隔离

**关键机制：**
1. **JWT 中包含 user_id 和 org_id**
   ```java
   // JwtTokenProvider.java
   public String generateToken(String userId, String email, String orgId) {
       Map<String, Object> claims = new HashMap<>();
       claims.put("user_id", userId);
       claims.put("org_id", orgId);
       // ...
   }
   ```

2. **ThreadLocal 存储用户上下文**
   ```java
   // UserContext.java
   public static String getUserId() {
       return userIdHolder.get();  // 线程安全
   }
   ```

3. **所有数据库查询都自动过滤**
   ```java
   // InterviewService.java
   public InterviewSession getSession(String sessionId) {
       String userId = UserContext.getUserId();  // 自动从上下文获取
       return sessionRepository.findByIdAndUserId(sessionId, userId);
   }
   ```

4. **PostgreSQL RLS 数据库级别强制隔离**
   ```sql
   -- V1__init_schema.sql
   CREATE POLICY user_isolation_on_interviews ON interview_sessions
       USING (user_id = current_setting('user.id')::UUID)
   ```

### 3. 面试流程管理

**创建面试会话**
```bash
curl -X POST http://localhost:8080/api/interview/session/create \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "skillProfileId": "550e8400-e29b-41d4-a716-446655440000",
    "interviewType": "self_introduction"
  }'
```

**开始面试**
```bash
curl -X POST http://localhost:8080/api/interview/{sessionId}/start \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**提交面试轮次**
```bash
curl -X POST http://localhost:8080/api/interview/{sessionId}/turn \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "response": "我的回答是...",
    "audioPath": "s3://bucket/audio/turn1.wav"
  }'
```

### 4. WebSocket 实时通信

**连接 WebSocket**
```javascript
// 前端 JavaScript
const ws = new WebSocket('ws://localhost:8080/api/ws/interview/session-id?token=' + accessToken);

ws.onopen = function() {
    console.log('Connected to interview');
    
    // 开始面试
    ws.send(JSON.stringify({
        type: 'start_interview',
        sessionId: 'session-id'
    }));
};

ws.onmessage = function(event) {
    const message = JSON.parse(event.data);
    console.log('Received:', message);
    
    if (message.type === 'interview_started') {
        console.log('Interview started:', message.data);
    }
};
```

### 5. Python Agent 服务集成

```java
// AgentServiceClient.java
public Map<String, Object> callPlanningService(Map<String, Object> request) {
    Mono<String> response = webClient.post()
            .uri("http://localhost:8000/api/v1/planning")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class);
    
    String responseBody = response.block();
    return objectMapper.readValue(responseBody, Map.class);
}
```

## 配置文件说明

### `application.yml` 主配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/interview_db
    username: postgres
    password: postgres

  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: your-secret-key-change-this-in-production
  expiration: 86400000  # 24小时

agent:
  service:
    url: http://localhost:8000  # Python Agent 服务地址
```

### 环境特定配置

```bash
# 使用 dev 环境配置
java -jar target/interview-agent-system-1.0.0.jar --spring.profiles.active=dev

# 使用 prod 环境配置
java -jar target/interview-agent-system-1.0.0.jar --spring.profiles.active=prod
```

## 数据库初始化

### 使用 Flyway 自动迁移

Flyway 会在应用启动时自动执行 `src/main/resources/db/migration/` 中的 SQL 脚本。

**手动初始化 (可选)**
```bash
# 登录 PostgreSQL
psql -h localhost -U postgres -d interview_db

# 执行初始化脚本
\i V1__init_schema.sql
```

### 验证表已创建

```bash
# 连接到数据库
psql -h localhost -U postgres -d interview_db

# 查看所有表
\dt

# 查看用户表结构
\d users
```

## 常见问题排查

### 问题 1：连接数据库失败

```
Error: FATAL: Ident authentication failed for user "postgres"
```

**解决方案：**
```bash
# 确保 PostgreSQL 容器正在运行
docker-compose ps

# 查看 PostgreSQL 日志
docker-compose logs postgres

# 尝试重启服务
docker-compose restart postgres
```

### 问题 2：端口被占用

```
Address already in use: bind
```

**解决方案：**
```bash
# 查看占用端口 8080 的进程
lsof -i :8080

# 杀死占用的进程
kill -9 <PID>

# 或改变应用端口
java -jar target/interview-agent-system-1.0.0.jar --server.port=8081
```

### 问题 3：Maven 依赖下载慢

**解决方案：**
- 修改 `~/.m2/settings.xml`，配置国内 Maven 镜像
- 或使用 VPN

### 问题 4：JWT Token 验证失败

```
Error: Invalid JWT token
```

**解决方案：**
- 确保 `jwt.secret` 配置与生成 token 的密钥一致
- 检查 token 是否过期 (24 小时)
- 使用 refresh token 获取新的 access token

## 开发工作流

### 添加新的 API 端点

1. **创建实体** (如需要) - `entity/YourEntity.java`
2. **创建 Repository** - `repository/YourRepository.java`
3. **创建 Service** - `service/YourService.java`
4. **创建 Controller** - `controller/YourController.java`
5. **写单元测试** - `src/test/java/...Test.java`

**示例：添加"意见反馈"功能**

```java
// 1. Entity
@Entity
@Table(name = "feedbacks")
public class Feedback {
    @Id
    private String id;
    @Column(name = "user_id")
    private String userId;
    private String content;
    // ...
}

// 2. Repository
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    List<Feedback> findByUserId(String userId);
}

// 3. Service
@Service
public class FeedbackService {
    public void submitFeedback(String userId, String content) {
        Feedback feedback = Feedback.builder()
                .userId(userId)
                .content(content)
                .build();
        feedbackRepository.save(feedback);
    }
}

// 4. Controller
@RestController
@RequestMapping("/feedback")
public class FeedbackController {
    @PostMapping("/submit")
    public ResponseEntity<String> submitFeedback(@RequestBody Map<String, String> request) {
        feedbackService.submitFeedback(UserContext.getUserId(), request.get("content"));
        return ResponseEntity.ok("Feedback submitted");
    }
}
```

## 测试

### 运行所有测试

```bash
mvn test
```

### 运行特定测试类

```bash
mvn test -Dtest=AuthServiceTest
```

### 生成测试覆盖率报告

```bash
mvn test jacoco:report
# 报告在 target/site/jacoco/index.html
```

## 部署

### Docker 部署

```bash
# 构建镜像
docker build -t interview-system:1.0.0 .

# 运行容器 (简单方式)
docker run -d -p 8080:8080 \
  -e POSTGRES_URL=jdbc:postgresql://postgres:5432/interview_db \
  -e REDIS_HOST=redis \
  interview-system:1.0.0

# 使用 Docker Compose 部署完整栈
docker-compose -f docker-compose.yml up -d
```

### Kubernetes 部署

参考 `k8s/` 目录中的清单文件 (YAML)

## 性能优化建议

1. **启用查询缓存**
   ```yaml
   spring:
     jpa:
       properties:
         hibernate:
           cache:
             use_second_level_cache: true
   ```

2. **使用连接池**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
   ```

3. **启用异步处理**
   ```java
   @Async
   public void processEvaluationAsync(String sessionId) {
       // 长时间运行的任务
   }
   ```

## 相关文档

- **完整系统架构**: 见 `ARCHITECTURE.md`
- **数据库设计**: 见 `V1__init_schema.sql`
- **API 文档**: Swagger UI (待实现)
- **Python Agent 文档**: `python-agent/README.md`

## 联系与支持

- 项目主页: https://github.com/your-org/interview-system
- 问题报告: https://github.com/your-org/interview-system/issues
- 讨论区: https://github.com/your-org/interview-system/discussions

## License

MIT License - 详见 LICENSE 文件
