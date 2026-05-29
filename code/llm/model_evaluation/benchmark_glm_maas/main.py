#!/usr/bin/env python3
"""
华为云MaaS GLM API Benchmark 评测脚本

支持多数据集并发评估：HLE、SWE-bench Lite、LongBench、TAU-bench
架构：数据集间多进程并发，数据集内异步协程并发
"""

import os
import sys
import json
import time
import logging
import argparse
import multiprocessing as mp
from typing import Dict, Any

import yaml  # pip install pyyaml

from api_client import GLMAPIClient, APIConfig
from evaluators.base import EvalConfig
from evaluators.hle import HLEEvaluator
from evaluators.swebench import SWEBenchEvaluator
from evaluators.longbench import LongBenchEvaluator
from evaluators.taubench import TAUBenchEvaluator
from metrics import create_summary

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


def load_config(config_path: str) -> Dict[str, Any]:
    """加载YAML配置文件"""
    with open(config_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def build_api_client(cfg: Dict[str, Any]) -> GLMAPIClient:
    """从配置构建API客户端"""
    api_cfg = cfg["api"]
    return GLMAPIClient(
        APIConfig(
            base_url=api_cfg["base_url"],
            api_key=api_cfg["api_key"],
            model=api_cfg.get("model", "glm-4-plus"),
            max_tokens=api_cfg.get("max_tokens", 2048),
            temperature=api_cfg.get("temperature", 0.0),
        )
    )


async def run_dataset_eval(
    dataset_name: str,
    dataset_cfg: Dict[str, Any],
    api_client: GLMAPIClient,
    global_concurrency: int,
    output_dir: str,
) -> Dict[str, Any]:
    """运行单个数据集的评测（异步主流程）"""
    if not dataset_cfg.get("enabled", True):
        logger.info(f"[{dataset_name}] Skipped (disabled)")
        return {"dataset_name": dataset_name, "skipped": True}

    concurrency = dataset_cfg.get("eval_batch_size", 0) or global_concurrency

    eval_config = EvalConfig(
        dataset_name=dataset_name,
        enabled=True,
        max_samples=dataset_cfg.get("max_samples", 0),
        eval_batch_size=concurrency,
        extra=dataset_cfg,
    )

    # 根据数据集类型选择评测器
    evaluator_map = {
        "hle": HLEEvaluator,
        # "swebench": SWEBenchEvaluator,
        # "longbench": LongBenchEvaluator,
        # "taubench": TAUBenchEvaluator,
    }

    evaluator_cls = evaluator_map.get(dataset_name)
    if evaluator_cls is None:
        logger.error(f"[{dataset_name}] Unknown dataset type")
        return {"dataset_name": dataset_name, "error": "Unknown dataset type"}

    evaluator = evaluator_cls(api_client, eval_config)
    result = await evaluator.run()

    # 保存详细结果
    os.makedirs(output_dir, exist_ok=True)
    result_path = os.path.join(output_dir, f"{dataset_name}_result.json")
    with open(result_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "dataset_name": result.dataset_name,
                "total": result.total,
                "correct": result.correct,
                "accuracy": result.accuracy,
                "avg_latency": result.avg_latency,
                "total_time": result.total_time,
                "errors": result.errors,
                "details": result.details[:50],  # 只保存前50条详情
            },
            f,
            indent=2,
            ensure_ascii=False,
        )
    logger.info(f"[{dataset_name}] Results saved to {result_path}")

    return {
        "dataset_name": result.dataset_name,
        "total": result.total,
        "correct": result.correct,
        "accuracy": result.accuracy,
        "avg_latency": result.avg_latency,
        "total_time": result.total_time,
        "errors": result.errors,
        "skipped": False,
    }


def _process_worker(
    dataset_name: str,
    dataset_cfg: Dict[str, Any],
    api_config_dict: Dict[str, Any],
    global_concurrency: int,
    output_dir: str,
    result_queue: mp.Queue,
):
    """多进程Worker函数（每个数据集一个进程）"""
    import asyncio

    # 在每个子进程中重新创建API客户端
    api_cfg = APIConfig(
        base_url=api_config_dict["base_url"],
        api_key=api_config_dict["api_key"],
        model=api_config_dict.get("model", "glm-4-plus"),
        max_tokens=api_config_dict.get("max_tokens", 2048),
        temperature=api_config_dict.get("temperature", 0.0),
    )
    api_client = GLMAPIClient(api_cfg)

    async def _run():
        return await run_dataset_eval(
            dataset_name, dataset_cfg, api_client, global_concurrency, output_dir
        )

    result = asyncio.run(_run())
    result_queue.put(result)


def main():
    parser = argparse.ArgumentParser(
        description="云MaaS GLM API 批量Benchmark评测"
    )
    parser.add_argument(
        "-c", "--config", default="config.yaml", help="配置文件路径"
    )
    parser.add_argument(
        "--dataset", nargs="+", default=None,
        help="指定要评测的数据集（空格分隔），如: --dataset hle longbench"
    )
    parser.add_argument(
        "--sequential", action="store_true",
        help="顺序执行各数据集（不启用多进程并发）"
    )
    args = parser.parse_args()

    # 加载配置
    config = load_config(args.config)
    api_config_dict = config["api"]
    concurrency_cfg = config["concurrency"]
    datasets_cfg = config["datasets"]
    output_cfg = config["output"]

    output_dir = output_cfg.get("dir", "./results")
    os.makedirs(output_dir, exist_ok=True)

    # 验证API Key
    if api_config_dict.get("api_key", "").startswith("your-"):
        print("⚠️  警告：请先在 config.yaml 中配置有效的 API Key")
        print("   GLM API Key可从以下渠道获取：")
        print("   1. 华为云MaaS控制台 -> 创建API Key")
        print("   2. 智谱BigModel开放平台: https://open.bigmodel.cn")
        print()

    # 选择要评测的数据集
    if args.dataset:
        selected = {k: v for k, v in datasets_cfg.items() if k in args.dataset}
    else:
        selected = {k: v for k, v in datasets_cfg.items() if v.get("enabled", True)}

    if not selected:
        print("没有选择任何数据集进行评测")
        return

    print(f"\n将评测以下数据集: {list(selected.keys())}")
    print(f"内部并发数: {concurrency_cfg['per_dataset']}")
    print(f"输出目录: {output_dir}\n")

    start_time = time.perf_counter()

    if args.sequential or len(selected) == 1:
        # 顺序执行模式
        import asyncio
        api_client = build_api_client(config)
        all_results = []

        async def _sequential():
            nonlocal all_results
            for name, cfg in selected.items():
                r = await run_dataset_eval(
                    name, cfg, api_client,
                    concurrency_cfg["per_dataset"], output_dir
                )
                all_results.append(r)

        asyncio.run(_sequential())
    else:
        # 多进程并发执行模式
        max_parallel = concurrency_cfg.get("max_parallel_datasets", 0)
        if max_parallel <= 0:
            max_parallel = len(selected)

        result_queue = mp.Queue()
        processes = []
        dataset_items = list(selected.items())

        for i in range(0, len(dataset_items), max_parallel):
            batch = dataset_items[i:i + max_parallel]
            batch_procs = []

            for name, cfg in batch:
                p = mp.Process(
                    target=_process_worker,
                    args=(
                        name, cfg, api_config_dict,
                        concurrency_cfg["per_dataset"],
                        output_dir, result_queue,
                    ),
                )
                p.start()
                batch_procs.append(p)

            # 等待本批完成
            for p in batch_procs:
                p.join()
            processes.extend(batch_procs)

        # 收集结果
        all_results = []
        while not result_queue.empty():
            all_results.append(result_queue.get())

    total_time = time.perf_counter() - start_time

    # 生成汇总报告
    summary = create_summary(all_results, total_time)
    summary.print_table()

    # 保存汇总
    summary_path = os.path.join(output_dir, "summary.json")
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary.to_dict(), f, indent=2, ensure_ascii=False)
    print(f"\n汇总结果已保存到: {summary_path}")


if __name__ == "__main__":
    main()