# 🚀 Spring Boot 后端框架 - 快速参考卡

## 📦 你已获得

| 类别 | 数量 | 说明 |
|------|------|------|
| Java 源文件 | 19 个 | 完整的后端功能实现 |
| 配置文件 | 4 个 | pom.xml, yml, docker-compose, Dockerfile |
| 文档 | 7 个 | 从快速开始到详细参考 |
| 数据库脚本 | 1 个 | PostgreSQL 初始化脚本 (Flyway) |
| **总计** | **31 个** | **即开即用** |

---

## ⚡ 3 分钟启动 (快速版)

```bash
# 1️⃣ 启动基础设施
docker-compose up -d

# 2️⃣ 编译项目
mvn clean package -DskipTests

# 3️⃣ 运行应用
java -jar target/interview-agent-system-1.0.0.jar

# ✅ 验证
curl http://localhost:8080/api/health
```

---

## 🔐 认证示例 (复制即用)

### 注册用户
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

### 登录
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secure123"}'

# 响应包含 accessToken，复制用于后续请求
# {"accessToken":"eyJhbGc...", "userId":"xxx", ...}
```

### 使用 Token 请求
```bash
# 设置变量
TOKEN="你的accessToken"

# 获取用户信息
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/auth/profile
```

---

## 📝 核心 API 速查表

### 认证接口
| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/profile` | 获取个人信息 |
| GET | `/api/health` | 健康检查 |

### 面试接口
| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/interview/session/create` | 创建会话 |
| POST | `/api/interview/{id}/start` | 开始面试 |
| POST | `/api/interview/{id}/turn` | 提交轮次 |
| GET | `/api/interview/{id}` | 获取会话状态 |
| POST | `/api/interview/{id}/evaluate` | 触发评估 |

### WebSocket
| 端点 | 消息类型 | 说明 |
|------|---------|------|
| `/ws/interview/{id}` | `start_interview` | 开始面试 |
| `/ws/interview/{id}` | `submit_turn` | 提交轮次 |
| `/ws/interview/{id}` | `end_interview` | 结束面试 |
| `/ws/interview/{id}` | `ping` | 心跳检测 |

---

## 🎯 项目结构 (核心)

```
interview-agent-system/
├── src/main/java/com/ai/interview/
│   ├── config/          # Spring 配置 (3个)
│   ├── security/        # JWT 认证 (3个)
│   ├── entity/          # 数据实体 (3个)
│   ├── repository/      # 数据访问 (3个)
│   ├── service/         # 业务逻辑 (2个)
│   ├── controller/      # REST API (2个)
│   ├── websocket/       # WebSocket (1个)
│   ├── client/          # 外部调用 (1个)
│   └── dto/             # 数据对象 (1个)
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__init_schema.sql
├── pom.xml              # Maven 依赖
├── Dockerfile           # Docker 镜像
└── docker-compose.yml   # 一键启动
```

---

## 🔧 修改配置 (生产部署)

### 修改 JWT 密钥 (安全!)
```yaml
# application.yml
jwt:
  secret: your-new-secret-key-min-256-bits  # 改这里!
  expiration: 86400000
```

### 修改数据库连接
```yaml
spring:
  datasource:
    url: jdbc:postgresql://your-db-host:5432/interview_db
    username: your-username
    password: your-password
```

### 修改 Python Agent 地址
```yaml
agent:
  service:
    url: http://your-agent-host:8000
```

---

## 📊 已实现的 9 层用户隔离

| 层级 | 机制 | 说明 |
|------|------|------|
| 1 | JWT 验证 | Token 中包含 user_id |
| 2 | ThreadLocal | 线程安全的用户上下文 |
| 3 | 访问控制 | API 认证检查 |
| 4 | Service 过滤 | 业务层隐式隔离 |
| 5 | Repository 过滤 | 数据访问层自动过滤 |
| 6 | PostgreSQL RLS | 数据库行级安全策略 |
| 7 | Redis 命名空间 | 缓存数据隔离 |
| 8 | 审计日志 | 记录所有数据访问 |
| 9 | 多组织支持 | org_id 组织级隔离 |

---

## 💻 常用命令

### Maven 命令
```bash
# 编译
mvn clean compile

# 打包
mvn clean package

# 运行测试
mvn test

# 跳过测试打包
mvn clean package -DskipTests

# 清理构建
mvn clean
```

### Docker 命令
```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down

# 删除数据卷 (谨慎!)
docker-compose down -v
```

### Java 运行命令
```bash
# 直接运行 JAR
java -jar target/interview-agent-system-1.0.0.jar

# 指定端口
java -Dserver.port=9000 -jar target/interview-agent-system-1.0.0.jar

# 指定配置文件
java -Dspring.config.location=application-prod.yml \
  -jar target/interview-agent-system-1.0.0.jar
```

---

## 🔗 文件导航

### 快速参考
| 需要 | 文件 |
|------|------|
| 快速开始 | 👉 `QUICK_START.md` |
| 项目结构 | 👉 `PROJECT_STRUCTURE.md` |
| 目录组织 | 👉 `DIRECTORY_STRUCTURE.md` |
| 全部文件 | 👉 `FILE_INVENTORY.md` |
| 完整交付 | 👉 `DELIVERY_SUMMARY.md` |
| 项目说明 | 👉 `README.md` |

### 代码位置
| 功能 | 文件 |
|------|------|
| 启动入口 | `InterviewAgentSystemApplication.java` |
| 用户隔离 | `JwtTokenProvider.java`, `UserContext.java` |
| 认证逻辑 | `AuthService.java`, `AuthController.java` |
| 面试逻辑 | `InterviewService.java`, `InterviewController.java` |
| 实时通信 | `InterviewWebSocketHandler.java` |
| Agent 调用 | `AgentServiceClient.java` |

---

## ✅ 验证清单

### 启动验证
- [ ] Docker 容器全部运行 (`docker-compose ps`)
- [ ] 应用成功启动 (日志无错误)
- [ ] 健康检查通过 (`curl http://localhost:8080/api/health`)

### 功能验证
- [ ] 用户能成功注册
- [ ] 用户能成功登录 (获得 token)
- [ ] 能创建面试会话
- [ ] WebSocket 能连接

### 数据验证
- [ ] PostgreSQL 表已创建 (12 个表)
- [ ] 用户数据已保存
- [ ] Redis 缓存可访问
- [ ] MinIO 文件可上传

---

## 🚨 故障排查

### 问题：Port 8080 已被占用
```bash
# 查找占用进程
lsof -i :8080

# 杀死进程 (Linux/Mac)
kill -9 <PID>

# 改用其他端口
java -Dserver.port=9000 -jar target/*.jar
```

### 问题：无法连接数据库
```bash
# 检查 PostgreSQL 容器是否运行
docker-compose ps | grep postgres

# 查看 PostgreSQL 日志
docker-compose logs postgres

# 重启 PostgreSQL
docker-compose restart postgres
```

### 问题：Maven 下载依赖很慢
```bash
# 修改 ~/.m2/settings.xml，配置国内镜像
# 或直接运行前先清理缓存
mvn clean
```

### 问题：WebSocket 连接失败
```bash
# 确保 URL 正确
# ws://localhost:8080/api/ws/interview/{sessionId}

# 检查 token 是否在 URL 参数或初始消息中
# ?token=your_jwt_token
```

---

## 📚 下一步开发

### 本周目标 (立即)
- [ ] 验证应用能成功启动
- [ ] 理解项目结构
- [ ] 运行第一个 API 测试

### 本月目标 (近期)
- [ ] 完成剩余 Entity 定义
- [ ] 实现 PDF 解析服务
- [ ] 集成音频处理

### 本季度目标 (中期)
- [ ] 开发 Vue3 前端
- [ ] 开发 Python Agent
- [ ] 完成端到端测试

---

## 🎓 推荐学习顺序

```
1. QUICK_START.md      (5分钟)    - 启动应用
2. PROJECT_STRUCTURE.md (10分钟)  - 理解架构
3. 源代码阅读           (30分钟)  - 理解实现
4. 修改配置            (5分钟)   - 个性化设置
5. 运行测试            (10分钟)  - 验证功能
6. 开始开发            (无限)   - 添加功能
```

---

## 💡 Pro 提示

### 1. 快速重启应用
```bash
# 编译 + 运行 (一行命令)
mvn clean package -DskipTests && java -jar target/*.jar
```

### 2. 调试 WebSocket
```javascript
// 浏览器 console 中连接测试
ws = new WebSocket('ws://localhost:8080/api/ws/interview/test-id?token=your-token');
ws.onmessage = (e) => console.log(JSON.parse(e.data));
```

### 3. 查看实时日志
```bash
docker-compose logs -f postgres   # PostgreSQL 日志
docker-compose logs -f redis      # Redis 日志
tail -f logs/application.log      # 应用日志
```

### 4. 快速测试 API
```bash
# 使用 PostMan 或 Insomnia
# 或用 curl + 别名
alias api='curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"'
api -X GET http://localhost:8080/api/auth/profile
```

---

## 🎉 完成！

你现在拥有：
- ✅ 完整的 Spring Boot 后端框架
- ✅ 所有必需的配置和脚本
- ✅ 详细的文档和指南
- ✅ 即开即用的代码

**下一步**: 按照 `QUICK_START.md` 启动应用吧！🚀

---

**版本**: 1.0.0
**最后更新**: 2024年
**状态**: ✅ 已完成
