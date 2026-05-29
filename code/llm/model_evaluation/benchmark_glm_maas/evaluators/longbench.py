"""
LongBench 评测器

LongBench是长文本理解的综合性基准，包含21个子数据集。
数据来源：https://github.com/THUDM/LongBench
"""

import os
import json
from typing import Dict, List, Any

from .base import BaseEvaluator, EvalConfig


class LongBenchEvaluator(BaseEvaluator):
    """LongBench数据集评测器"""

    def load_dataset(self) -> List[Dict[str, Any]]:
        """加载LongBench数据集"""
        data_path = self.config.extra.get("data_path", "")
        subsets = self.config.extra.get("subsets", [])  # 指定子集

        if not data_path or not os.path.isdir(data_path):
            print(f"[LongBench] 数据集目录不存在: {data_path}")
            print("[LongBench] 请从以下地址下载：")
            print("  - GitHub: https://github.com/THUDM/LongBench")
            print("  - 或 Hugging Face: THUDM/LongBench")
            print("[LongBench] 当前将使用内置演示数据\n")
            return self._get_demo_samples()

        samples = []
        # LongBench数据格式：data/ 目录下各子目录包含 .jsonl 文件
        if not subsets:
            subsets = self._get_available_subsets(data_path)

        for subset in subsets:
            subset_path = os.path.join(data_path, f"{subset}.jsonl")
            if not os.path.isfile(subset_path):
                subset_path = os.path.join(data_path, subset, f"{subset}.jsonl")
            if not os.path.isfile(subset_path):
                print(f"[LongBench] 跳过不存在的子集: {subset}")
                continue

            with open(subset_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    item = json.loads(line)
                    # LongBench标准格式
                    samples.append({
                        "id": f"{subset}_{len(samples)}",
                        "messages": self._build_messages(item, subset),
                        "reference": item.get("answers", item.get("answer", "")),
                        "subset": subset,
                        "context_length": len(str(item.get("context", ""))),
                    })

        return samples

    def _build_messages(self, item: Dict, subset: str) -> List[Dict[str, str]]:
        """根据LongBench子任务类型构建消息"""
        context = item.get("context", "")
        input_text = item.get("input", "")

        # 不同任务类型的提示
        prompts = {
            "narrativeqa": f"Read the following text and answer the question:\n\n{context}\n\nQuestion: {input_text}\nAnswer:",
            "qasper": f"Read the following paper and answer the question:\n\n{context}\n\nQuestion: {input_text}\nAnswer:",
            "multifieldqa_en": f"Read the article and answer the question:\n\n{context}\n\nQuestion: {input_text}\nAnswer:",
            "gov_report": f"Summarize the following report:\n\n{context}\n\nSummary:",
            "qmsum": f"Summarize the meeting notes:\n\n{context}\n\nSummary:",
        }

        prompt = prompts.get(subset, f"{context}\n\n{input_text}")
        return [
            {"role": "system", "content": "You are a helpful assistant."},
            {"role": "user", "content": prompt},
        ]

    def compute_accuracy(self, predictions: List[str], references: List[str]) -> Dict[str, Any]:
        """
        LongBench精度计算

        LongBench原始评测使用多种指标（ROUGE-L、F1等），
        这里使用简化的基于n-gram重叠的F1分数。
        """
        correct = 0
        total_f1 = 0.0
        details = []

        for pred, ref in zip(predictions, references):
            f1 = self._compute_f1(pred, ref)
            total_f1 += f1
            is_correct = f1 >= 0.3
            if is_correct:
                correct += 1
            details.append({"f1": f1, "correct": is_correct})

        n = len(predictions) if predictions else 1
        return {
            "avg_f1": total_f1 / n,
            "accuracy": correct / n,
            "correct_count": correct,
            "total": n,
            "details": details,
        }

    def _compute_f1(self, pred: str, ref: str) -> float:
        """计算token级别的F1分数"""
        if not pred or not ref:
            return 0.0

        import re
        pred_tokens = set(re.findall(r'\w+', pred.lower()))
        ref_tokens = set(re.findall(r'\w+', ref.lower()))

        if not ref_tokens:
            return 0.0

        common = pred_tokens & ref_tokens
        precision = len(common) / len(pred_tokens) if pred_tokens else 0
        recall = len(common) / len(ref_tokens)
        if precision + recall == 0:
            return 0.0
        return 2 * precision * recall / (precision + recall)

    def _get_available_subsets(self, data_path: str) -> List[str]:
        """获取可用的子集列表"""
        subsets = []
        for f in os.listdir(data_path):
            if f.endswith(".jsonl"):
                subsets.append(f.replace(".jsonl", ""))
        return subsets

    def _get_demo_samples(self) -> List[Dict[str, Any]]:
        """内置演示样本（简化长文本场景）"""
        long_text = (
            "Machine learning is a subset of artificial intelligence that enables "
            "systems to learn from data and improve over time without being explicitly "
            "programmed. Deep learning, a subset of machine learning, uses neural "
            "networks with multiple layers to analyze various factors of data. "
            "Natural language processing (NLP) focuses on the interaction between "
            "computers and human language, enabling tasks like translation, "
            "sentiment analysis, and text generation. "
        ) * 50  # 模拟长文本

        return [
            {
                "id": "longbench_demo_001",
                "messages": [
                    {"role": "user", "content": f"{long_text}\n\nWhat is deep learning?"}
                ],
                "reference": "A subset of machine learning that uses neural networks with multiple layers",
                "subset": "demo",
                "context_length": len(long_text),
            },
        ]