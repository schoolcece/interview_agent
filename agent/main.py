from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.interview import router as interview_router

app = FastAPI(
    title="AI Interview Agent",
    description="Python AI Agent 服务：简历解析 / LLM 面试 / 评估",
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(interview_router, prefix="/api/v1")


@app.get("/health")
async def health():
    return {"status": "ok", "service": "python-agent"}
