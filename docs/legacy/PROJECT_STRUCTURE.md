> ⚠️ **本文档已过时（v0 阶段遗留）**
>
> - 描述的 PostgreSQL + RLS + org_id 多租户架构**不再采用**（当前使用 MySQL，C 端无多租户）
> - 列出的大量 Entity / Service / Repository **实际并未实现**
> - 请参考最新文档：[`ARCHITECTURE.md`](./ARCHITECTURE.md) 与 [`AGENT_DESIGN.md`](./AGENT_DESIGN.md)
>
> 保留此文件仅供历史追溯，**不要作为开发参考**。

---

# AI Interview System - Spring Boot Backend Framework

## 项目概述
基于 Java + Spring Boot + Python FastAPI 的混合架构，为多用户 AI 面试系统提供后端支持。

## 项目结构

```
interview-agent-system/
├── pom.xml                                  # Maven 配置文件
├── application.yml                          # Spring Boot 配置文件
├── src/
│   ├── main/
│   │   ├── java/com/ai/interview/
│   │   │   ├── InterviewAgentSystemApplication.java     # 主应用入口
│   │   │   │
│   │   │   ├── config/                                   # 配置层
│   │   │   │   ├── SecurityConfig.java                   # Spring Security 配置
│   │   │   │   ├── WebSocketConfig.java                  # WebSocket 配置
│   │   │   │   └── AppConfig.java                        # 应用级别配置
│   │   │   │
│   │   │   ├── security/                                 # 安全层
│   │   │   │   ├── JwtTokenProvider.java                 # JWT 生成和验证
│   │   │   │   ├── JwtAuthenticationFilter.java          # JWT 过滤器
│   │   │   │   └── UserContext.java                      # 用户隔离上下文
│   │   │   │
│   │   │   ├── entity/                                   # JPA 实体
│   │   │   │   ├── User.java                             # 用户实体
│   │   │   │   ├── Resume.java                           # 简历实体
│   │   │   │   ├── InterviewSession.java                 # 面试会话实体
│   │   │   │   ├── InterviewTurn.java                    # 面试轮次实体
│   │   │   │   ├── SkillProfile.java                     # 技能画像实体
│   │   │   │   ├── KnowledgeMastery.java                 # 知识掌握度实体
│   │   │   │   ├── SpeechTranscription.java              # 语音转录实体
│   │   │   │   └── EvaluationReport.java                 # 评估报告实体
│   │   │   │
│   │   │   ├── repository/                               # 数据访问层
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── ResumeRepository.java
│   │   │   │   ├── InterviewSessionRepository.java
│   │   │   │   ├── InterviewTurnRepository.java
│   │   │   │   ├── SkillProfileRepository.java
│   │   │   │   ├── KnowledgeMasteryRepository.java
│   │   │   │   └── EvaluationReportRepository.java
│   │   │   │
│   │   │   ├── service/                                  # 业务服务层
│   │   │   │   ├── AuthService.java                      # 认证服务
│   │   │   │   ├── ResumeService.java                    # 简历处理服务
│   │   │   │   ├── InterviewService.java                 # 面试服务
│   │   │   │   ├── SkillProfileService.java              # 技能画像服务
│   │   │   │   ├── KnowledgeMasteryService.java          # 知识掌握度服务
│   │   │   │   ├── AudioProcessingService.java           # 音频处理服务
│   │   │   │   └── ReflectionService.java                # 复盘学习服务
│   │   │   │
│   │   │   ├── controller/                               # 控制层
│   │   │   │   ├── AuthController.java                   # 认证接口
│   │   │   │   ├── ResumeController.java                 # 简历接口
│   │   │   │   ├── InterviewController.java              # 面试接口
│   │   │   │   ├── SkillProfileController.java           # 技能画像接口
│   │   │   │   └── LearningController.java               # 学习推荐接口
│   │   │   │
│   │   │   ├── websocket/                                # WebSocket 处理
│   │   │   │   ├── InterviewWebSocketHandler.java        # 面试 WebSocket 处理器
│   │   │   │   └── AudioStreamHandler.java               # 音频流处理器
│   │   │   │
│   │   │   ├── client/                                   # 外部服务调用
│   │   │   │   ├── AgentServiceClient.java               # Python Agent 服务客户端
│   │   │   │   ├── MinIOClient.java                      # MinIO 文件存储客户端
│   │   │   │   └── RedisClient.java                      # Redis 缓存客户端
│   │   │   │
│   │   │   ├── dto/                                      # 数据传输对象
│   │   │   │   ├── AuthDTO.java
│   │   │   │   ├── InterviewDTO.java
│   │   │   │   ├── SkillProfileDTO.java
│   │   │   │   └── ResponseDTO.java
│   │   │   │
│   │   │   ├── exception/                                # 异常处理
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── AuthException.java
│   │   │   │   ├── InterviewException.java
│   │   │   │   └── StorageException.java
│   │   │   │
│   │   │   ├── util/                                     # 工具类
│   │   │   │   ├── JwtUtils.java
│   │   │   │   ├── AesEncryptUtil.java
│   │   │   │   ├── PDFParseUtil.java
│   │   │   │   └── AudioUtil.java
│   │   │   │
│   │   │   └── aspect/                                   # AOP 切面
│   │   │       ├── UserIsolationAspect.java              # 用户隔离切面
│   │   │       ├── LoggingAspect.java                    # 日志切面
│   │   │       └── PerformanceAspect.java                # 性能监控切面
│   │   │
│   │   └── resources/
│   │       ├── application.yml                           # 主配置文件
│   │       ├── application-dev.yml                       # 开发环境配置
│   │       ├── application-prod.yml                      # 生产环境配置
│   │       └── db/
│   │           └── migration/                            # Flyway 数据库迁移脚本
│   │               ├── V1__init_schema.sql               # 初始化
│   │               ├── V2__add_speech_tables.sql         # 语音表
│   │               └── V3__add_indices.sql               # 索引
│   │
│   └── test/
│       └── java/com/ai/interview/
│           ├── AuthServiceTest.java
│           ├── InterviewServiceTest.java
│           └── IntegrationTest.java
│
├── docker-compose.yml                       # Docker Compose 配置
├── Dockerfile                               # Docker 镜像构建文件
├── .gitignore
├── README.md
└── ARCHITECTURE.md                          # 架构文档

```

## 核心模块说明

### 1. 安全层 (Security Layer)
- **JwtTokenProvider**: 处理 JWT token 的生成和验证
- **JwtAuthenticationFilter**: 拦截请求，验证 token 并设置用户上下文
- **UserContext**: ThreadLocal 存储当前用户信息，实现请求级别的用户隔离

### 2. 实体层 (Entity Layer)
定义了所有 JPA 实体，对应数据库表：
- **User**: 用户信息（用户名、邮箱、角色等）
- **Resume**: 简历信息（上传时间、解析状态等）
- **InterviewSession**: 面试会话（状态、当前轮次等）
- **InterviewTurn**: 面试轮次详情（问题、回答、音频等）
- **SkillProfile**: 用户技能画像（技能点、掌握度等）
- **KnowledgeMastery**: 知识掌握度追踪（用于自适应出题）
- **SpeechTranscription**: 语音转录和质量评分
- **EvaluationReport**: 评估报告

### 3. 数据访问层 (Repository Layer)
使用 Spring Data JPA，提供 CRUD 和自定义查询方法

### 4. 业务服务层 (Service Layer)
核心业务逻辑实现：
- **AuthService**: 用户认证和注册
- **ResumeService**: 简历上传、解析、技能提取
- **InterviewService**: 面试流程管理
- **SkillProfileService**: 技能画像生成和管理
- **KnowledgeMasteryService**: 知识掌握度计算（贝叶斯更新）
- **AudioProcessingService**: 音频处理（转录、质量评分）
- **ReflectionService**: 复盘学习推荐

### 5. 控制层 (Controller Layer)
RESTful API 端点，处理 HTTP 请求

### 6. WebSocket 层
实时双向通信，用于面试过程中的实时交互

### 7. Python Agent 客户端
通过 HTTP 调用 Python FastAPI 服务，实现 5 阶段 Agent 工作流

## 关键特性

### 用户数据隔离
1. **JWT Token 中包含 user_id 和 org_id**
   ```java
   String token = jwtTokenProvider.generateToken(userId, email, orgId);
   ```

2. **ThreadLocal 存储用户上下文**
   ```java
   UserContext.setUserId(userId);
   UserContext.setOrgId(orgId);
   ```

3. **所有数据库查询都过滤用户**
   ```java
   List<InterviewSession> sessions = sessionRepository.findByUserId(UserContext.getUserId());
   ```

4. **PostgreSQL RLS 策略** (在 SQL 中配置)
   ```sql
   ALTER TABLE interview_sessions ENABLE ROW LEVEL SECURITY;
   CREATE POLICY user_isolation ON interview_sessions 
     USING (user_id = current_setting('user.id'));
   ```

### 与 Python Agent 的通信
```
Spring Boot (8080) ←→ HTTP ←→ FastAPI Agent Service (8000)
```

AgentServiceClient 提供以下方法：
- `callPlanningService()` - 规划面试
- `callInterviewService()` - 交互式面试
- `callEvaluationService()` - 自动评分和点评
- `callMemoryService()` - 更新知识掌握度
- `callReflectionService()` - 生成复盘建议

## 环境变量配置

```yaml
# application.yml
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
  secret: your-secret-key-min-256-bits
  expiration: 86400000  # 24 hours

agent:
  service:
    url: http://localhost:8000

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
```

## API 端点列表

### 认证 (Auth)
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `GET /api/auth/profile` - 获取用户信息

### 简历 (Resume)
- `POST /api/resume/upload` - 上传 PDF 简历
- `GET /api/resume/list` - 获取简历列表
- `GET /api/resume/{id}` - 获取简历详情
- `DELETE /api/resume/{id}` - 删除简历

### 面试 (Interview)
- `POST /api/interview/session/create` - 创建面试会话
- `POST /api/interview/{sessionId}/start` - 开始面试
- `POST /api/interview/{sessionId}/turn` - 提交轮次回答
- `GET /api/interview/{sessionId}` - 获取会话状态
- `POST /api/interview/{sessionId}/evaluate` - 触发评估

### 技能画像 (Skill Profile)
- `GET /api/skill-profile` - 获取当前技能画像
- `GET /api/skill-profile/history` - 获取历史版本

### 学习推荐 (Learning)
- `GET /api/learning/recommendations` - 获取个性化推荐
- `GET /api/learning/error-problems` - 获取错题本

### WebSocket
- `WS /api/ws/interview/{sessionId}` - 面试实时通信

## 部署指南

### 本地开发
```bash
# 1. 启动 PostgreSQL 和 Redis
docker-compose up -d

# 2. 编译项目
mvn clean package

# 3. 运行应用
java -jar target/interview-agent-system-1.0.0.jar
```

### Docker 部署
```bash
# 构建镜像
docker build -t interview-system:1.0.0 .

# 运行容器
docker run -d -p 8080:8080 \
  -e POSTGRES_URL=jdbc:postgresql://postgres:5432/interview_db \
  -e REDIS_HOST=redis \
  interview-system:1.0.0
```

## 下一步开发

1. **完成实体和 Repository 定义** (所有 entity 对应表)
2. **实现 Resume PDF 解析** (使用 PDFBox 和 Python NLP)
3. **实现音频处理** (集成 Whisper ASR 和 Edge TTS)
4. **实现 PostgreSQL RLS 和 Row Security Policies**
5. **编写单元测试和集成测试**
6. **前端 Vue3 应用开发** (调用 REST/WebSocket API)
7. **Python FastAPI Agent 服务开发** (5 阶段工作流)

## 技术栈版本

- **Java**: 17+
- **Spring Boot**: 3.2.0
- **Spring Security**: 6.0+
- **Spring Data JPA**: 3.2.0
- **PostgreSQL**: 15+
- **Redis**: 7.0+
- **Milvus**: 2.3+
- **MinIO**: latest
- **JWT**: 0.12.3
- **PDFBox**: 3.0.0
- **Lombok**: 1.18.30

## 文档引用

- 完整系统设计: `ARCHITECTURE.md`
- 数据库设计: 见 `db/migration/` 中的 SQL 文件
- API 文档: 使用 Swagger/Springfox 自动生成 (可选)
- Python Agent 设计: 见 `python-agent/README.md`
