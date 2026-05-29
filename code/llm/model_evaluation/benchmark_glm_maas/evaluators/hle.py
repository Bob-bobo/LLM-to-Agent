"""
HLE (Humanity's Last Exam) 评测器

HLE是一个包含多学科高难度题目的数据集。
数据格式预期为JSONL，每行：
{"id": "xxx", "question": "...", "answer": "...", "subject": "physics"}
"""

import os
import json
import re
import pandas as pd          # 新增导入
from typing import Dict, List, Any

from .base import BaseEvaluator, EvalConfig


class HLEEvaluator(BaseEvaluator):
    """HLE数据集评测器"""

    def load_dataset(self) -> List[Dict[str, Any]]:
        """加载HLE数据集，支持JSONL和Parquet格式"""
        data_path = self.config.extra.get("data_path", "")

        # 若文件不存在，回退到演示数据
        if not data_path or not os.path.isfile(data_path):
            print(f"[HLE] 数据集文件不存在: {data_path}")
            print("[HLE] 当前将使用内置演示数据")
            return self._get_demo_samples()

        # 根据文件扩展名选择加载方式
        ext = os.path.splitext(data_path)[1].lower()
        if ext == ".parquet":
            return self._load_from_parquet(data_path)
        elif ext in (".jsonl", ".json"):
            return self._load_from_jsonl(data_path)
        else:
            print(f"[HLE] 不支持的文件格式: {ext}")
            return self._get_demo_samples()

    def _load_from_parquet(self, file_path: str) -> List[Dict[str, Any]]:
        """从Parquet文件加载HLE数据"""
        df = pd.read_parquet(file_path)
        samples = []
        for _, row in df.iterrows():
            # 过滤掉包含图片的题目（当前版本不处理多模态）
            if row.get("image", False):
                continue

            question = row["question"]
            answer = row.get("answer", row.get("correct_answer", ""))
            # 构建消息（可保留原始分类信息，但评测时不使用）
            samples.append({
                "id": str(row.get("id", len(samples))),
                "messages": [
                    {
                        "role": "system",
                        "content": "You are a helpful assistant. Provide the final answer concisely."
                    },
                    {
                        "role": "user",
                        "content": question,
                    },
                ],
                "reference": str(answer) if answer is not None else "",
                "subject": row.get("category", "general"),
            })
        print(f"[HLE] 从Parquet加载了 {len(samples)} 个文本样本（已跳过含图片的题目）")
        return samples

    def _load_from_jsonl(self, file_path: str) -> List[Dict[str, Any]]:
        """从JSONL文件加载HLE数据（原逻辑）"""
        samples = []
        with open(file_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                item = json.loads(line)
                samples.append({
                    "id": item.get("id", ""),
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a helpful assistant. Answer the question accurately and concisely."
                        },
                        {
                            "role": "user",
                            "content": item["question"],
                        },
                    ],
                    "reference": item.get("answer", ""),
                    "subject": item.get("subject", "general"),
                })
        return samples

    def compute_accuracy(self, predictions: List[str], references: List[str]) -> Dict[str, Any]:
        """
        计算HLE精度

        HLE题目通常有确定的答案，使用以下策略：
        1. 精确匹配（忽略大小写和首尾空白）
        2. 提取预测中的最终答案进行对比
        """
        correct = 0
        details = []
        for pred, ref in zip(predictions, references):
            # 清理文本
            pred_clean = self._extract_answer(pred)
            ref_clean = ref.strip().lower()

            is_correct = pred_clean == ref_clean
            if is_correct:
                correct += 1
            details.append({
                "prediction": pred_clean,
                "reference": ref_clean,
                "correct": is_correct,
            })

        return {
            "exact_match": correct / len(predictions) if predictions else 0,
            "correct_count": correct,
            "total": len(predictions),
            "details": details,
        }

    def _extract_answer(self, text: str) -> str:
        """从模型输出中提取最终答案"""
        if not text:
            return ""
        text = text.strip().lower()
        # 尝试匹配 "答案是：xxx" 或 "Answer: xxx" 模式
        patterns = [
            r"(?:答案(?:是|为)[：:]\s*)(.+?)(?:\n|$)",
            r"(?:answer\s*(?:is\s*)?[:：]\s*)(.+?)(?:\n|$)",
            r"(?:最终答案[：:]\s*)(.+?)(?:\n|$)",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                return match.group(1).strip()
        # 取最后一行作为答案
        lines = text.strip().split("\n")
        return lines[-1].strip()

    def _get_demo_samples(self) -> List[Dict[str, Any]]:
        """内置演示样本（仅用于验证脚本流程，不代表真实HLE数据）"""
        return [
            {
                "id": "hle_demo_001",
                "messages": [
                    {"role": "user", "content": "What is the capital of France?"}
                ],
                "reference": "paris",
                "subject": "geography",
            },
            {
                "id": "hle_demo_002",
                "messages": [
                    {"role": "user", "content": "求解方程 x^2 - 4 = 0 的根。"}
                ],
                "reference": "x = ±2",
                "subject": "mathematics",
            },
            {
                "id": "hle_demo_003",
                "messages": [
                    {
                        "role": "user",
                        "content": "In quantum mechanics, what does the wave function Ψ represent?",
                    }
                ],
                "reference": "probability amplitude",
                "subject": "physics",
            },
        ]

    @staticmethod
    def _file_exists(path: str) -> bool:
        import os
        return os.path.isfile(path)