"""LLM 客户端封装，支持 OpenAI 兼容接口（DeepSeek / 本地 Ollama 等均可）。"""
from openai import OpenAI

from app.core.config import settings

_client: OpenAI | None = None


def get_llm() -> OpenAI:
    global _client
    if _client is None:
        _client = OpenAI(
            api_key=settings.openai_api_key,
            base_url=settings.openai_base_url,
        )
    return _client


def chat(system_prompt: str, user_prompt: str, temperature: float = 0.7) -> str:
    """单次同步对话，返回文本内容。"""
    client = get_llm()
    response = client.chat.completions.create(
        model=settings.openai_model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        temperature=temperature,
    )
    return response.choices[0].message.content or ""
