"""
SQLAlchemy ORM 模型 - 只读已有表，不负责建表（由 Java Flyway 管理）。
Python 侧写入：interview_turns, async_jobs (status/result), resumes (parsed_content)。
Python 侧只读：users, interview_sessions, knowledge_points 等。
"""
from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger, Boolean, Column, DateTime, Enum, Integer,
    JSON, String, Text, func,
)

from app.db.database import Base


class Resume(Base):
    __tablename__ = "resumes"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger, nullable=False)
    original_filename = Column(String(255))
    file_path = Column(String(512), nullable=False)
    file_size_bytes = Column(BigInteger)
    content_hash = Column(String(64))
    raw_text = Column(Text)
    parsed_content = Column(JSON)
    mentioned_kps = Column(JSON)
    embedding_ref = Column(String(128))
    analysis_status = Column(
        Enum("PENDING", "PROCESSING", "PARSED", "FAILED", name="analysis_status_enum"),
        nullable=False,
        default="PENDING",
    )
    analysis_error = Column(Text)
    is_active = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime(3))


class AsyncJob(Base):
    __tablename__ = "async_jobs"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger)
    job_type = Column(String(32), nullable=False)
    related_entity_type = Column(String(32))
    related_entity_id = Column(BigInteger)
    status = Column(
        Enum("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED", name="job_status_enum"),
        nullable=False,
        default="PENDING",
    )
    progress = Column(Integer, nullable=False, default=0)
    payload = Column(JSON)
    result = Column(JSON)
    error_message = Column(Text)
    attempt_count = Column(Integer, nullable=False, default=0)
    max_attempts = Column(Integer, nullable=False, default=3)
    scheduled_at = Column(DateTime(3))
    started_at = Column(DateTime(3))
    completed_at = Column(DateTime(3))
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())


class InterviewSession(Base):
    __tablename__ = "interview_sessions"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger, nullable=False)
    resume_id = Column(BigInteger)
    title = Column(String(200))
    status = Column(String(32), nullable=False, default="PLANNING")
    config = Column(JSON)
    plan = Column(JSON)
    current_turn = Column(Integer, nullable=False, default=0)
    total_turns = Column(Integer, nullable=False, default=10)
    duration_seconds = Column(Integer)
    version = Column(Integer, nullable=False, default=0)
    started_at = Column(DateTime(3))
    ended_at = Column(DateTime(3))
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())


class InterviewTurn(Base):
    __tablename__ = "interview_turns"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    session_id = Column(BigInteger, nullable=False)
    turn_no = Column(Integer, nullable=False)
    question_id = Column(BigInteger)
    question_text = Column(Text, nullable=False)
    question_type = Column(String(32))
    user_response = Column(Text)
    audio_path = Column(String(512))
    asr_transcript = Column(Text)
    asr_confidence = Column(Integer)
    score_detail = Column(JSON)
    feedback_text = Column(Text)
    status = Column(String(32), nullable=False, default="PENDING")
    response_time_ms = Column(Integer)
    followup_of = Column(BigInteger)
    created_at = Column(DateTime(3), server_default=func.now())
    updated_at = Column(DateTime(3), server_default=func.now(), onupdate=func.now())
