"""
评测器基类 - 定义统一的数据集评测流程
"""

import logging
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Dict, List, Any

from api_client import GLMAPIClient

logger = logging.getLogger(__name__)


@dataclass
class EvalConfig:
    """评测配置"""
    dataset_name: str
    enabled: bool = True
    max_samples: int = 0
    eval_batch_size: int = 10
    extra: Dict[str, Any] = field(default_factory=dict)


@dataclass
class EvalResult:
    """评测结果"""
    dataset_name: str
    total: int
    correct: int
    accuracy: float
    avg_latency: float
    total_time: float
    errors: int
    details: List[Dict[str, Any]] = field(default_factory=list)


class BaseEvaluator(ABC):
    """评测器基类"""

    def __init__(self, api_client: GLMAPIClient, config: EvalConfig):
        self.api_client = api_client
        self.config = config
        self.results: List[Dict[str, Any]] = []

    @abstractmethod
    def load_dataset(self) -> List[Dict[str, Any]]:
        """
        加载数据集，返回标准化样本列表

        每个样本是一个字典，至少包含：
        - "id": 样本唯一标识
        - "messages": [{"role": "user", "content": "..."}]
        - "reference": 参考答案（用于精度计算）
        """
        pass

    @abstractmethod
    def compute_accuracy(self, predictions: List[str], references: List[str]) -> Dict[str, Any]:
        """
        计算精度指标

        Returns:
            {"exact_match": float, "f1": float, "correct_count": int, ...}
        """
        pass

    async def run(self) -> EvalResult:
        """执行评测的主流程"""
        start_time = time.perf_counter()
        dataset = self.load_dataset()

        # 限制样本数
        if self.config.max_samples > 0:
            dataset = dataset[: self.config.max_samples]

        if not dataset:
            logger.warning(f"[{self.config.dataset_name}] No samples loaded")
            return EvalResult(
                dataset_name=self.config.dataset_name,
                total=0, correct=0, accuracy=0.0,
                avg_latency=0.0, total_time=0.0, errors=0
            )

        # 提取消息和参考答案
        batch_messages = [s["messages"] for s in dataset]
        references = [s["reference"] for s in dataset]

        logger.info(
            f"[{self.config.dataset_name}] Calling API with {len(batch_messages)} samples, "
            f"concurrency={self.config.eval_batch_size}"
        )

        # 并发调用API
        responses = await self.api_client.call_batch(
            batch_messages, concurrency=self.config.eval_batch_size
        )

        # 收集预测结果
        predictions = []
        errors = 0
        latencies = []
        for i, resp in enumerate(responses):
            if resp and resp["success"]:
                predictions.append(resp["content"])
                latencies.append(resp["latency"])
                self.results.append({
                    "id": dataset[i]["id"],
                    "messages": batch_messages[i],
                    "reference": references[i],
                    "prediction": resp["content"],
                    "latency": resp["latency"],
                    "usage": resp.get("usage"),
                    "error": None,
                })
            else:
                predictions.append("")
                errors += 1
                error_msg = resp.get("error", "Unknown") if resp else "Null response"
                logger.error(f"[{self.config.dataset_name}] Sample {i} failed: {error_msg}")
                self.results.append({
                    "id": dataset[i]["id"],
                    "messages": batch_messages[i],
                    "reference": references[i],
                    "prediction": "",
                    "latency": 0,
                    "usage": None,
                    "error": error_msg,
                })

        # 计算精度
        accuracy_info = self.compute_accuracy(predictions, references)

        avg_latency = sum(latencies) / len(latencies) if latencies else 0
        total_time = time.perf_counter() - start_time

        result = EvalResult(
            dataset_name=self.config.dataset_name,
            total=len(dataset),
            correct=accuracy_info.get("correct_count", 0),
            accuracy=accuracy_info.get("exact_match", accuracy_info.get("accuracy", 0)),
            avg_latency=avg_latency,
            total_time=total_time,
            errors=errors,
            details=self.results,
        )

        logger.info(
            f"[{self.config.dataset_name}] Done: accuracy={result.accuracy:.4f}, "
            f"avg_latency={avg_latency:.2f}s, errors={errors}"
        )
        return result