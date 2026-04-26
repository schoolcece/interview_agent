"""
简历解析 Worker。
轮询 async_jobs 表中 job_type=RESUME_PARSE 且 status=PENDING 的任务，
下载 PDF → 提取文本 → LLM 结构化 → 写回 resumes 表。

启动方式（独立进程）：
    python -m app.workers.resume_parser
"""
import json
import logging
import time
from datetime import datetime, timezone

import pdfplumber
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.llm import chat
from app.core.storage import download_bytes
from app.db.database import SessionLocal
from app.db.models import AsyncJob, Resume

logger = logging.getLogger(__name__)

_PARSE_SYSTEM = """你是一位简历解析专家。
请从以下简历文本中提取结构化信息，输出合法 JSON（不含任何 markdown 代码块）：
{
  "name": "姓名",
  "email": "邮箱",
  "phone": "电话",
  "education": [{"school": "...", "degree": "...", "major": "...", "year": "..."}],
  "skills": ["技能1", "技能2"],
  "work_experience": [{"company": "...", "title": "...", "duration": "...", "description": "..."}],
  "projects": [{"name": "...", "tech_stack": ["..."], "description": "..."}],
  "work_experience_summary": "一句话总结工作经历",
  "projects_summary": "一句话总结项目经历"
}
只输出 JSON。"""


def run_once(db: Session) -> int:
    """处理一批待处理任务，返回处理的任务数量。"""
    jobs = (
        db.query(AsyncJob)
        .filter(
            AsyncJob.job_type == "RESUME_PARSE",
            AsyncJob.status == "PENDING",
            AsyncJob.attempt_count < AsyncJob.max_attempts,
        )
        .limit(5)
        .all()
    )

    for job in jobs:
        _process_job(job, db)

    return len(jobs)


def _process_job(job: AsyncJob, db: Session) -> None:
    logger.info("Processing job %s for resume %s", job.id, job.related_entity_id)

    job.status = "RUNNING"
    job.attempt_count += 1
    job.started_at = datetime.now(timezone.utc)
    db.commit()

    resume = db.get(Resume, job.related_entity_id)
    if not resume:
        _fail_job(job, "Resume record not found", db)
        return

    try:
        resume.analysis_status = "PROCESSING"
        db.commit()

        payload = job.payload or {}
        bucket = payload.get("bucket", settings.minio_bucket_resume)
        file_path = payload.get("file_path", resume.file_path)

        pdf_bytes = download_bytes(bucket, file_path)
        raw_text = _extract_text(pdf_bytes)
        resume.raw_text = raw_text

        parsed = _llm_parse(raw_text)
        resume.parsed_content = parsed
        resume.analysis_status = "PARSED"

        job.status = "SUCCESS"
        job.progress = 100
        job.completed_at = datetime.now(timezone.utc)
        job.result = {"parsed_fields": list(parsed.keys())}
        db.commit()

        logger.info("Resume %s parsed successfully", resume.id)

    except Exception as e:
        logger.exception("Failed to parse resume %s", resume.id if resume else "?")
        resume.analysis_status = "FAILED"
        resume.analysis_error = str(e)
        _fail_job(job, str(e), db)


def _extract_text(pdf_bytes: bytes) -> str:
    import io
    pages = []
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        for page in pdf.pages:
            text = page.extract_text()
            if text:
                pages.append(text)
    return "\n\n".join(pages)


def _llm_parse(raw_text: str) -> dict:
    truncated = raw_text[:6000]
    raw = chat(_PARSE_SYSTEM, truncated, temperature=0.1)
    text = raw.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        text = "\n".join(lines[1:-1] if lines[-1].strip() == "```" else lines[1:])
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        logger.warning("LLM returned invalid JSON for resume parsing")
        return {"raw_text_fallback": raw_text[:500]}


def _fail_job(job: AsyncJob, error: str, db: Session) -> None:
    job.status = "FAILED" if job.attempt_count >= job.max_attempts else "PENDING"
    job.error_message = error
    job.completed_at = datetime.now(timezone.utc) if job.status == "FAILED" else None
    db.commit()


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    logger.info("Resume parser worker started. Polling every %ds", settings.worker_poll_interval)

    while True:
        try:
            with SessionLocal() as db:
                count = run_once(db)
                if count:
                    logger.info("Processed %d job(s)", count)
        except Exception:
            logger.exception("Worker loop error")

        time.sleep(settings.worker_poll_interval)


if __name__ == "__main__":
    main()
