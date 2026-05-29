"""
GLM API 异步调用客户端
支持华为云MaaS和智谱BigModel两种接入方式
"""

import asyncio
import time
import logging
from typing import Dict, List, Any, Optional, AsyncGenerator
from dataclasses import dataclass

import aiohttp

logger = logging.getLogger(__name__)


@dataclass
class APIConfig:
    """API配置"""
    base_url: str
    api_key: str
    model: str = "glm-4-plus"
    max_tokens: int = 2048
    temperature: float = 0.0
    max_retries: int = 3
    retry_delay: float = 1.0
    timeout: float = 120.0


class GLMAPIClient:
    """GLM API异步客户端（兼容华为云MaaS和智谱BigModel）"""

    def __init__(self, config: APIConfig):
        self.config = config
        self._semaphore: Optional[asyncio.Semaphore] = None
        # 去掉末尾的 /chat/completions（如果有的话），方便后续拼接
        base = config.base_url.rstrip("/")
        if base.endswith("/chat/completions"):
            base = base[:-len("/chat/completions")]
        self._endpoint = f"{base}/chat/completions"

    async def call(
        self,
        messages: List[Dict[str, str]],
        max_tokens: Optional[int] = None,
        temperature: Optional[float] = None,
        session: Optional[aiohttp.ClientSession] = None,
    ) -> Dict[str, Any]:
        """
        调用GLM API（支持限流自动重试）

        Args:
            messages: 对话消息列表 [{"role": "user", "content": "..."}]
            max_tokens: 最大输出token数，默认使用配置值
            temperature: 采样温度，默认使用配置值
            session: 复用已有的aiohttp session

        Returns:
            {"success": bool, "content": str, "usage": dict, "latency": float, "error": str}
        """
        start_time = time.perf_counter()
        payload = {
            "model": self.config.model,
            "messages": messages,
            "max_tokens": max_tokens or self.config.max_tokens,
            "temperature": temperature or self.config.temperature,
            "stream": False,
        }
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.config.api_key}",
        }

        close_session = False
        if session is None:
            session = aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=self.config.timeout)
            )
            close_session = True

        try:
            for attempt in range(self.config.max_retries + 1):
                try:
                    async with session.post(
                        self._endpoint, json=payload, headers=headers
                    ) as resp:
                        resp_json = await resp.json()

                        if resp.status == 200:
                            latency = time.perf_counter() - start_time
                            content = resp_json["choices"][0]["message"]["content"]
                            usage = resp_json.get("usage", {})
                            return {
                                "success": True,
                                "content": content,
                                "usage": usage,
                                "latency": latency,
                                "error": None,
                            }
                        elif resp.status == 429:
                            # 限流，等待后重试
                            if attempt < self.config.max_retries:
                                delay = self.config.retry_delay * (2 ** attempt)
                                logger.warning(
                                    f"Rate limited, retrying in {delay:.1f}s "
                                    f"(attempt {attempt + 1}/{self.config.max_retries})"
                                )
                                await asyncio.sleep(delay)
                                continue
                            else:
                                return self._error_response(
                                    start_time, f"Rate limit exceeded: {resp_json}"
                                )
                        else:
                            return self._error_response(
                                start_time, f"HTTP {resp.status}: {resp_json}"
                            )
                except asyncio.TimeoutError:
                    if attempt < self.config.max_retries:
                        await asyncio.sleep(self.config.retry_delay)
                        continue
                    return self._error_response(start_time, "Request timeout")
                except Exception as e:
                    if attempt < self.config.max_retries:
                        await asyncio.sleep(self.config.retry_delay)
                        continue
                    return self._error_response(start_time, str(e))

        finally:
            if close_session:
                await session.close()

        return self._error_response(start_time, "Unknown error")

    async def call_batch(
        self,
        batch_messages: List[List[Dict[str, str]]],
        concurrency: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        并发批量调用API

        Args:
            batch_messages: 批量消息列表
            concurrency: 最大并发数

        Returns:
            结果列表（顺序与输入一致）
        """
        semaphore = asyncio.Semaphore(concurrency)

        async def _call_with_semaphore(idx: int, msgs: List[Dict[str, str]]):
            async with semaphore:
                result = await self.call(msgs)
                result["_idx"] = idx
                return result

        async with aiohttp.ClientSession(
            timeout=aiohttp.ClientTimeout(total=self.config.timeout)
        ) as session:
            tasks = [
                _call_with_semaphore(i, msgs) for i, msgs in enumerate(batch_messages)
            ]
            results = await asyncio.gather(*tasks, return_exceptions=True)

        # 按原始顺序排列并处理异常
        ordered = [None] * len(batch_messages)
        for r in results:
            if isinstance(r, Exception):
                logger.error(f"Batch call exception: {r}")
                continue
            ordered[r["_idx"]] = r

        return ordered

    def _error_response(self, start_time: float, error_msg: str) -> Dict[str, Any]:
        return {
            "success": False,
            "content": None,
            "usage": None,
            "latency": time.perf_counter() - start_time,
            "error": error_msg,
        }

    async def call_stream(
        self,
        messages: List[Dict[str, str]],
        session: Optional[aiohttp.ClientSession] = None,
    ) -> AsyncGenerator[str, None]:
        """流式调用API（用于需要实时交互的场景，如TAU-bench）"""
        payload = {
            "model": self.config.model,
            "messages": messages,
            "max_tokens": self.config.max_tokens,
            "temperature": self.config.temperature,
            "stream": True,
        }
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.config.api_key}",
        }

        close_session = False
        if session is None:
            session = aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=self.config.timeout)
            )
            close_session = True

        try:
            async with session.post(
                self._endpoint, json=payload, headers=headers
            ) as resp:
                if resp.status != 200:
                    text = await resp.text()
                    logger.error(f"Stream request failed: HTTP {resp.status} - {text}")
                    return
                async for line in resp.content:
                    line = line.decode("utf-8").strip()
                    if line.startswith("data: "):
                        data = line[6:]
                        if data == "[DONE]":
                            return
                        import json
                        try:
                            chunk = json.loads(data)
                            delta = chunk["choices"][0].get("delta", {})
                            content = delta.get("content", "")
                            if content:
                                yield content
                        except json.JSONDecodeError:
                            continue
        finally:
            if close_session:
                await session.close()