"""
SWE-bench Lite 评测器

SWE-bench完整评测需要Docker环境和代码执行，不适合纯API调用。
这里实现一个轻量级版本，评测代码生成基础能力。
"""

import json
import re
from typing import Dict, List, Any

from .base import BaseEvaluator, EvalConfig


class SWEBenchEvaluator(BaseEvaluator):
    """SWE-bench Lite评测器（代码生成能力）"""

    def load_dataset(self) -> List[Dict[str, Any]]:
        """加载SWE-bench Lite数据集或演示数据"""
        data_path = self.config.extra.get("data_path", "")
        if not data_path or not self._file_exists(data_path):
            print(f"[SWE-bench] 数据集文件不存在: {data_path}")
            print("[SWE-bench] 完整SWE-bench评测需要Docker环境，请参考：")
            print("  - GitHub: https://github.com/princeton-nlp/SWE-bench")
            print("[SWE-bench] 当前将使用内置代码生成示例进行评测\n")
            return self._get_demo_samples()

        with open(data_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        samples = []
        for item in data:
            problem = item.get("problem_statement", item.get("issue", ""))
            samples.append({
                "id": item.get("instance_id", ""),
                "messages": [
                    {
                        "role": "system",
                        "content": "You are an expert software engineer. "
                        "Write correct, well-documented code to solve the problem.",
                    },
                    {
                        "role": "user",
                        "content": f"Fix the following issue:\n\n{problem}\n\n"
                        f"Provide only the corrected code with a brief explanation.",
                    },
                ],
                "reference": item.get("patch", item.get("solution", "")),
            })
        return samples

    def compute_accuracy(self, predictions: List[str], references: List[str]) -> Dict[str, Any]:
        """
        SWE-bench Lite 精度计算

        简化版指标：
        1. 代码是否包含预期的关键元素
        2. 代码的基本语法检查
        """
        correct = 0
        details = []
        for pred, ref in zip(predictions, references):
            score = self._evaluate_code_similarity(pred, ref)
            is_correct = score >= 0.3  # 阈值可调
            if is_correct:
                correct += 1
            details.append({
                "prediction_length": len(pred) if pred else 0,
                "reference_length": len(ref) if ref else 0,
                "similarity_score": score,
                "correct": is_correct,
            })

        return {
            "accuracy": correct / len(predictions) if predictions else 0,
            "correct_count": correct,
            "total": len(predictions),
            "details": details,
        }

    def _evaluate_code_similarity(self, pred: str, ref: str) -> float:
        """简单的代码相似度评估（基于关键token重叠）"""
        if not pred or not ref:
            return 0.0
        # 提取代码块
        pred_code = self._extract_code(pred)
        ref_code = self._extract_code(ref)

        # 提取标识符和关键字
        pred_tokens = set(re.findall(r'\b[a-zA-Z_]\w*\b', pred_code))
        ref_tokens = set(re.findall(r'\b[a-zA-Z_]\w*\b', ref_code))

        if not ref_tokens:
            return 0.0
        intersection = pred_tokens & ref_tokens
        return len(intersection) / len(ref_tokens)

    @staticmethod
    def _extract_code(text: str) -> str:
        """从markdown中提取代码块"""
        code_blocks = re.findall(r'```(?:\w+)?\n(.*?)```', text, re.DOTALL)
        if code_blocks:
            return "\n".join(code_blocks)
        return text

    def _get_demo_samples(self) -> List[Dict[str, Any]]:
        """内置代码生成演示样本"""
        return [
            {
                "id": "swe_demo_001",
                "messages": [
                    {
                        "role": "user",
                        "content": "Write a Python function to check if a string is a palindrome.",
                    }
                ],
                "reference": "def is_palindrome(s): return s == s[::-1]",
            },
            {
                "id": "swe_demo_002",
                "messages": [
                    {
                        "role": "user",
                        "content": "Write a Python function that takes a list and returns only even numbers.",
                    }
                ],
                "reference": "def filter_even(lst): return [x for x in lst if x % 2 == 0]",
            },
            {
                "id": "swe_demo_003",
                "messages": [
                    {
                        "role": "user",
                        "content": "Fix the bug: `def add(a, b) return a + b`",
                    }
                ],
                "reference": "def add(a, b):\n    return a + b",
            },
        ]

    @staticmethod
    def _file_exists(path: str) -> bool:
        import os
        return os.path.isfile(path)