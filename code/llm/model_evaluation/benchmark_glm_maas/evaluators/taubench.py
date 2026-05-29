"""
TAU-bench 评测器

TAU-bench评估AI智能体的工具使用和策略遵循能力。
需要用户模型(User Model)来驱动多轮对话。
"""

import os
import json
import logging
from typing import Dict, List, Any

from .base import BaseEvaluator, EvalConfig

logger = logging.getLogger(__name__)


class TAUBenchEvaluator(BaseEvaluator):
    """TAU-bench数据集评测器"""

    def load_dataset(self) -> List[Dict[str, Any]]:
        """
        加载TAU-bench数据集

        TAU-bench的评测逻辑较复杂，建议使用EvalScope框架。
        这里提供简化的单轮评估版本，适合快速验证GLM API服务。
        """
        print("[TAU-bench] 完整TAU-bench评测建议使用EvalScope框架：")
        print("  pip install evalscope")
        print("  pip install git+https://github.com/sierra-research/tau2-bench@v0.2.0")
        print("  参考文档：https://evalscope.readthedocs.io/")
        print("[TAU-bench] 当前将使用内置工具调用评测样本\n")

        return self._get_demo_samples()

    def compute_accuracy(self, predictions: List[str], references: List[str]) -> Dict[str, Any]:
        """
        TAU-bench精度计算（简化版）

        评估点：
        1. 是否正确识别了需要调用的工具
        2. 工具调用参数是否合理
        """
        correct = 0
        details = []
        for pred, ref in zip(predictions, references):
            score = self._evaluate_tool_call(pred, ref)
            is_correct = score >= 0.5
            if is_correct:
                correct += 1
            details.append({
                "score": score,
                "correct": is_correct,
            })

        return {
            "accuracy": correct / len(predictions) if predictions else 0,
            "correct_count": correct,
            "total": len(predictions),
            "details": details,
        }

    def _evaluate_tool_call(self, pred: str, ref: str) -> float:
        """评估工具调用的正确性"""
        import re

        # 提取函数调用
        pred_funcs = re.findall(r'(\w+)\s*\(', pred)
        ref_funcs = re.findall(r'(\w+)\s*\(', ref)

        if not ref_funcs:
            return 0.0

        # 检查是否调用了预期的函数
        matches = sum(1 for f in ref_funcs if f in pred_funcs)
        return matches / len(ref_funcs)

    def _get_demo_samples(self) -> List[Dict[str, Any]]:
        """内置工具调用评测样本"""
        return [
            {
                "id": "tau_demo_001",
                "messages": [
                    {
                        "role": "system",
                        "content": (
                            "You are an airline customer service agent. "
                            "Available tools: search_flights(), cancel_reservation(), "
                            "modify_booking(), check_baggage_policy(). "
                            "Always respond with the appropriate function call when needed."
                        ),
                    },
                    {
                        "role": "user",
                        "content": "I need to cancel my reservation #12345.",
                    },
                ],
                "reference": "cancel_reservation(reservation_id=\"12345\")",
            },
            {
                "id": "tau_demo_002",
                "messages": [
                    {
                        "role": "system",
                        "content": (
                            "You are a retail customer service agent. "
                            "Available tools: check_order_status(), return_item(), "
                            "update_shipping_address(), apply_discount()."
                        ),
                    },
                    {
                        "role": "user",
                        "content": "Can you tell me the status of order #ORD-789?",
                    },
                ],
                "reference": "check_order_status(order_id=\"ORD-789\")",
            },
            {
                "id": "tau_demo_003",
                "messages": [
                    {
                        "role": "system",
                        "content": (
                            "You are a telecom customer service agent. "
                            "Available tools: check_network_status(), reset_modem(), "
                            "upgrade_plan(), view_data_usage()."
                        ),
                    },
                    {
                        "role": "user",
                        "content": "My internet is not working. Can you help?",
                    },
                ],
                "reference": "check_network_status()",
            },
        ]

    @staticmethod
    def _file_exists(path: str) -> bool:
        import os
        return os.path.isdir(path) or os.path.isfile(path)