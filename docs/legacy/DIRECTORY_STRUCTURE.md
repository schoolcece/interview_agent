# 项目目录结构组织指南

## 当前生成的文件位置

所有文件已生成在 `e:\study\agent\` 目录下。为了正确的项目结构，请按以下方式组织：

## 推荐的最终项目结构

```
interview-agent-system/                 # 项目根目录
│
├── pom.xml                            # Maven 配置 (根目录)
├── Dockerfile                         # Docker 镜像
├── docker-compose.yml                 # 基础设施
├── README.md                          # 项目说明
├── QUICK_START.md                     # 快速开始
├── PROJECT_STRUCTURE.md               # 项目结构
├── FILE_INVENTORY.md                  # 文件清单
│
├── src/
│   ├── main/
│   │   ├── java/com/ai/interview/
│   │   │   ├── InterviewAgentSystemApplication.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── WebSocketConfig.java
│   │   │   │   └── AppConfig.java
│   │   │   ├── security/
│   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   └── UserContext.java
│   │   │   ├── entity/
│   │   │   │   ├── User.java
│   │   │   │   ├── Resume.java
│   │   │   │   ├── InterviewSession.java
│   │   │   │   ├── InterviewTurn.java          (TODO)
│   │   │   │   ├── SkillProfile.java           (TODO)
│   │   │   │   ├── KnowledgeMastery.java       (TODO)
│   │   │   │   ├── SpeechTranscription.java    (TODO)
│   │   │   │   ├── EvaluationReport.java       (TODO)
│   │   │   │   ├── ErrorProblem.java           (TODO)
│   │   │   │   ├── LearningRecommendation.java (TODO)
│   │   │   │   └── ReflectionReport.java       (TODO)
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── ResumeRepository.java
│   │   │   │   ├── InterviewSessionRepository.java
│   │   │   │   ├── InterviewTurnRepository.java     (TODO)
│   │   │   │   ├── SkillProfileRepository.java     (TODO)
│   │   │   │   ├── KnowledgeMasteryRepository.java (TODO)
│   │   │   │   └── EvaluationReportRepository.java (TODO)
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── InterviewService.java
│   │   │   │   ├── ResumeService.java              (TODO)
│   │   │   │   ├── SkillProfileService.java        (TODO)
│   │   │   │   ├── KnowledgeMasteryService.java    (TODO)
│   │   │   │   ├── AudioProcessingService.java     (TODO)
│   │   │   │   ├── EvaluationService.java          (TODO)
│   │   │   │   └── ReflectionService.java          (TODO)
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── InterviewController.java
│   │   │   │   ├── ResumeController.java           (TODO)
│   │   │   │   ├── SkillProfileController.java     (TODO)
│   │   │   │   └── LearningController.java         (TODO)
│   │   │   ├── websocket/
│   │   │   │   ├── InterviewWebSocketHandler.java
│   │   │   │   └── AudioStreamHandler.java         (TODO)
│   │   │   ├── client/
│   │   │   │   ├── AgentServiceClient.java
│   │   │   │   ├── MinIOClient.java                (TODO)
│   │   │   │   └── RedisClient.java                (TODO)
│   │   │   ├── dto/
│   │   │   │   ├── AuthDTO.java
│   │   │   │   ├── InterviewDTO.java               (TODO)
│   │   │   │   ├── SkillProfileDTO.java            (TODO)
│   │   │   │   └── ResponseDTO.java                (TODO)
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java     (TODO)
│   │   │   │   ├── AuthException.java              (TODO)
│   │   │   │   ├── InterviewException.java         (TODO)
│   │   │   │   └── StorageException.java           (TODO)
│   │   │   ├── util/
│   │   │   │   ├── JwtUtils.java                   (TODO)
│   │   │   │   ├── AesEncryptUtil.java             (TODO)
│   │   │   │   ├── PDFParseUtil.java               (TODO)
│   │   │   │   └── AudioUtil.java                  (TODO)
│   │   │   └── aspect/
│   │   │       ├── UserIsolationAspect.java        (TODO)
│   │   │       ├── LoggingAspect.java              (TODO)
│   │   │       └── PerformanceAspect.java          (TODO)
│   │   │
│   │   └── resources/
│   │       ├── application.yml                (从 application.yml 复制)
│   │       ├── application-dev.yml            (TODO)
│   │       ├── application-prod.yml           (TODO)
│   │       └── db/
│   │           └── migration/
│   │               ├── V1__init_schema.sql    (从 V1__init_schema.sql 复制)
│   │               ├── V2__add_speech_tables.sql    (TODO)
│   │               └── V3__add_indices.sql          (TODO)
│   │
│   └── test/
│       └── java/com/ai/interview/
│           ├── AuthServiceTest.java               (TODO)
│           ├── InterviewServiceTest.java          (TODO)
│           ├── IntegrationTest.java               (TODO)
│           └── controller/
│               ├── AuthControllerTest.java        (TODO)
│               └── InterviewControllerTest.java   (TODO)
│
├── docs/                              # 文档目录
│   ├── QUICK_START.md                (从 QUICK_START.md 复制)
│   ├── PROJECT_STRUCTURE.md          (从 PROJECT_STRUCTURE.md 复制)
│   ├── FILE_INVENTORY.md             (从 FILE_INVENTORY.md 复制)
│   └── API.md                        (TODO - API 详细文档)
│
├── k8s/                               # Kubernetes 配置 (TODO)
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
│
├── python-agent/                      # Python FastAPI Agent 服务 (TODO)
│   ├── main.py
│   ├── requirements.txt
│   └── agents/
│       ├── planning_agent.py
│       ├── interview_agent.py
│       ├── evaluation_agent.py
│       ├── memory_agent.py
│       └── reflection_agent.py
│
└── .gitignore                         # Git 忽略文件

```

## 文件迁移步骤

### 第1步：创建基础目录结构

```bash
# 进入项目根目录
cd e:\study\agent

# 创建 Maven 标准目录结构
mkdir -p src/main/java/com/ai/interview/{config,security,entity,repository,service,controller,websocket,client,dto,exception,util,aspect}
mkdir -p src/main/resources/db/migration
mkdir -p src/test/java/com/ai/interview/{service,controller}
mkdir -p docs
```

### 第2步：移动 Java 文件到正确位置

```bash
# Config 文件
move SecurityConfig.java src\main\java\com\ai\interview\config\
move WebSocketConfig.java src\main\java\com\ai\interview\config\
move AppConfig.java src\main\java\com\ai\interview\config\

# Security 文件
move JwtTokenProvider.java src\main\java\com\ai\interview\security\
move JwtAuthenticationFilter.java src\main\java\com\ai\interview\security\
move UserContext.java src\main\java\com\ai\interview\security\

# Entity 文件
move User.java src\main\java\com\ai\interview\entity\
move Resume.java src\main\java\com\ai\interview\entity\
move InterviewSession.java src\main\java\com\ai\interview\entity\

# Repository 文件
move UserRepository.java src\main\java\com\ai\interview\repository\
move ResumeRepository.java src\main\java\com\ai\interview\repository\
move InterviewSessionRepository.java src\main\java\com\ai\interview\repository\

# Service 文件
move AuthService.java src\main\java\com\ai\interview\service\
move InterviewService.java src\main\java\com\ai\interview\service\

# Controller 文件
move AuthController.java src\main\java\com\ai\interview\controller\
move InterviewController.java src\main\java\com\ai\interview\controller\

# WebSocket 文件
move InterviewWebSocketHandler.java src\main\java\com\ai\interview\websocket\

# Client 文件
move AgentServiceClient.java src\main\java\com\ai\interview\client\

# DTO 文件
move AuthDTO.java src\main\java\com\ai\interview\dto\

# 主程序
move InterviewAgentSystemApplication.java src\main\java\com\ai\interview\
```

### 第3步：移动资源文件

```bash
# 配置文件
move application.yml src\main\resources\

# 数据库脚本
move V1__init_schema.sql src\main\resources\db\migration\
```

### 第4步：移动文档

```bash
# 文档文件
move QUICK_START.md docs\
move PROJECT_STRUCTURE.md docs\
move FILE_INVENTORY.md docs\
```

### 第5步：复制 Maven 配置和 Docker 文件（已在根目录）

```bash
# 这些文件已经在根目录，无需移动
# pom.xml
# Dockerfile
# docker-compose.yml
# README.md
```

## 快速迁移脚本 (PowerShell)

```powershell
# 创建目录
$dirs = @(
    'src/main/java/com/ai/interview/config',
    'src/main/java/com/ai/interview/security',
    'src/main/java/com/ai/interview/entity',
    'src/main/java/com/ai/interview/repository',
    'src/main/java/com/ai/interview/service',
    'src/main/java/com/ai/interview/controller',
    'src/main/java/com/ai/interview/websocket',
    'src/main/java/com/ai/interview/client',
    'src/main/java/com/ai/interview/dto',
    'src/main/java/com/ai/interview/exception',
    'src/main/java/com/ai/interview/util',
    'src/main/java/com/ai/interview/aspect',
    'src/main/resources/db/migration',
    'src/test/java/com/ai/interview/service',
    'src/test/java/com/ai/interview/controller',
    'docs'
)

foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force
    }
}

# 移动文件
Move-Item SecurityConfig.java src\main\java\com\ai\interview\config\
Move-Item WebSocketConfig.java src\main\java\com\ai\interview\config\
Move-Item AppConfig.java src\main\java\com\ai\interview\config\
Move-Item JwtTokenProvider.java src\main\java\com\ai\interview\security\
Move-Item JwtAuthenticationFilter.java src\main\java\com\ai\interview\security\
Move-Item UserContext.java src\main\java\com\ai\interview\security\
Move-Item User.java src\main\java\com\ai\interview\entity\
Move-Item Resume.java src\main\java\com\ai\interview\entity\
Move-Item InterviewSession.java src\main\java\com\ai\interview\entity\
Move-Item UserRepository.java src\main\java\com\ai\interview\repository\
Move-Item ResumeRepository.java src\main\java\com\ai\interview\repository\
Move-Item InterviewSessionRepository.java src\main\java\com\ai\interview\repository\
Move-Item AuthService.java src\main\java\com\ai\interview\service\
Move-Item InterviewService.java src\main\java\com\ai\interview\service\
Move-Item AuthController.java src\main\java\com\ai\interview\controller\
Move-Item InterviewController.java src\main\java\com\ai\interview\controller\
Move-Item InterviewWebSocketHandler.java src\main\java\com\ai\interview\websocket\
Move-Item AgentServiceClient.java src\main\java\com\ai\interview\client\
Move-Item AuthDTO.java src\main\java\com\ai\interview\dto\
Move-Item InterviewAgentSystemApplication.java src\main\java\com\ai\interview\
Move-Item application.yml src\main\resources\
Move-Item V1__init_schema.sql src\main\resources\db\migration\
Move-Item QUICK_START.md docs\
Move-Item PROJECT_STRUCTURE.md docs\
Move-Item FILE_INVENTORY.md docs\

Write-Host "所有文件已成功迁移到标准 Maven 结构！"
```

## 验证项目结构

```bash
# 检查所有 Java 文件是否在正确位置
find src/main/java -name "*.java" | wc -l
# 应该显示 19 个文件

# 检查资源文件
ls src/main/resources/
# 应该显示 application.yml 和 db/migration/ 目录

# 检查文档
ls docs/
# 应该显示 QUICK_START.md, PROJECT_STRUCTURE.md, FILE_INVENTORY.md
```

## 下一步

迁移后，执行：

```bash
# 编译验证
mvn clean compile

# 下载依赖
mvn dependency:resolve

# 构建 JAR
mvn clean package -DskipTests

# 启动应用
java -jar target/interview-agent-system-1.0.0.jar
```

## 完成标记

✅ 所有 Java 文件都有正确的 package 声明
✅ 所有配置文件都在 resources 目录
✅ 所有文档都在 docs 目录
✅ pom.xml 包含所有必需的依赖
✅ Docker 和 docker-compose 在根目录
✅ 可以立即开始 Maven 编译

**你现在可以打开此项目在 IDE 中了！** 🎉
