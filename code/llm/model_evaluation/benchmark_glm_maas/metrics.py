"""
统一的精度计算与结果汇总
"""

import json
import logging
from typing import Dict, List, Any
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class BenchmarkSummary:
    """汇总所有数据集的评测结果"""
    total_datasets: int
    total_samples: int
    total_errors: int
    overall_accuracy: float
    total_time: float
    dataset_results: List[Dict[str, Any]]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "total_datasets": self.total_datasets,
            "total_samples": self.total_samples,
            "total_errors": self.total_errors,
            "overall_accuracy": self.overall_accuracy,
            "total_time_seconds": self.total_time,
            "dataset_results": self.dataset_results,
        }

    def print_table(self):
        """打印结果表格"""
        print("\n" + "=" * 70)
        print("  GLM API Benchmark Results Summary")
        print("=" * 70)
        print(f"{'Dataset':<20} {'Samples':>8} {'Correct':>8} {'Accuracy':>10} {'Errors':>7} {'Time(s)':>8}")
        print("-" * 70)

        for r in self.dataset_results:
            print(
                f"{r['dataset_name']:<20} "
                f"{r['total']:>8} "
                f"{r['correct']:>8} "
                f"{r['accuracy']:>9.2%} "
                f"{r['errors']:>7} "
                f"{r['total_time']:>7.1f}"
            )

        print("-" * 70)
        print(
            f"{'TOTAL':<20} "
            f"{self.total_samples:>8} "
            f"{'':>8} "
            f"{self.overall_accuracy:>9.2%} "
            f"{self.total_errors:>7} "
            f"{self.total_time:>7.1f}"
        )
        print("=" * 70)


def create_summary(all_results: List[Dict[str, Any]], total_time: float) -> BenchmarkSummary:
    """从所有数据集的结果创建汇总"""
    total_samples = sum(r.get("total", 0) for r in all_results)
    total_errors = sum(r.get("errors", 0) for r in all_results)

    # 加权平均精度
    weighted_acc = 0.0
    for r in all_results:
        if r.get("total", 0) > 0:
            weighted_acc += r.get("accuracy", 0) * r["total"]
    overall_accuracy = weighted_acc / total_samples if total_samples > 0 else 0.0

    return BenchmarkSummary(
        total_datasets=len(all_results),
        total_samples=total_samples,
        total_errors=total_errors,
        overall_accuracy=overall_accuracy,
        total_time=total_time,
        dataset_results=all_results,
    )