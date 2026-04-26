# AI Interview Agent - Python 服务

## 快速启动

```bash
cd agent/

# 1. 创建虚拟环境
python -m venv .venv
source .venv/bin/activate     # Linux/Mac
# .venv\Scripts\activate      # Windows

# 2. 安装依赖
pip install -r requirements.txt

# 3. 启动 API 服务
uvicorn main:app --reload --port 8000

# 4. 启动简历解析 Worker（另开终端）
python -m app.workers.resume_parser
```

## 配置

在 `../.env` 里添加 Python 侧需要的配置：

```env
# LLM（OpenAI 兼容，也可以换成 DeepSeek / 本地 Ollama）
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o-mini
```

## API 端点

| 方法 | 路径 | 调用方 | 说明 |
|------|------|--------|------|
| POST | `/api/v1/planning` | Java | 生成面试计划，写回 interview_sessions.plan |
| POST | `/api/v1/interview` | Java | 处理一轮答题，写 interview_turns |
| POST | `/api/v1/evaluation` | Java | 面试结束后生成综合报告 |
| GET  | `/health` | 监控 | 健康检查 |

## 目录结构

```
agent/
├── main.py               FastAPI 入口
├── requirements.txt
└── app/
    ├── api/v1/
    │   └── interview.py  三个核心端点
    ├── core/
    │   ├── config.py     配置（读 .env）
    │   ├── llm.py        LLM 客户端封装
    │   └── storage.py    MinIO 客户端封装
    ├── db/
    │   ├── database.py   SQLAlchemy engine + session
    │   └── models.py     ORM 模型（只读已有表）
    ├── services/
    │   └── interview_agent.py  Planning / Interview / Evaluation 逻辑
    └── workers/
        └── resume_parser.py    简历解析 Worker（轮询 async_jobs）
```
