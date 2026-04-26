from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.services.interview_agent import run_planning, run_interview_turn, run_evaluation

router = APIRouter()


@router.post("/planning")
async def planning(payload: dict, db: Session = Depends(get_db)):
    """Java 调用：为面试会话生成面试计划。"""
    return run_planning(payload, db)


@router.post("/interview")
async def interview_turn(payload: dict, db: Session = Depends(get_db)):
    """Java 调用：处理一轮答题，返回评分 + 下一道题。"""
    return run_interview_turn(payload, db)


@router.post("/evaluation")
async def evaluation(payload: dict, db: Session = Depends(get_db)):
    """Java 调用：面试结束后生成综合评估报告。"""
    return run_evaluation(payload, db)
