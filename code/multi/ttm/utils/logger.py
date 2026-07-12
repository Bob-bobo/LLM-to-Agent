"""
简单的日志记录器
支持 scalars 日志记录（用于 TensorBoard 风格可视化）
"""
import os
import json
from datetime import datetime
from typing import Optional, Dict


class Logger:
    """
    简单日志记录器，支持保存标量日志到文件
    可以配合 TensorBoard 可视化，也可以直接读取绘制曲线
    """

    def __init__(
        self,
        log_dir: str,
        enabled: bool = True
    ):
        """
        参数:
            log_dir: 日志目录
            enabled: 是否启用日志
        """
        self.log_dir = log_dir
        self.enabled = enabled
        self.scalar_log_path = os.path.join(log_dir, 'scalars.jsonl')

        if enabled:
            os.makedirs(log_dir, exist_ok=True)
            # 创建文件
            if not os.path.exists(self.scalar_log_path):
                with open(self.scalar_log_path, 'w', encoding='utf-8') as f:
                    pass

    def info(self, message: str):
        """打印信息"""
        if self.enabled:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}")

    def log_scalar(
        self,
        tag: str,
        value: float,
        step: int
    ):
        """记录一个标量"""
        if not self.enabled:
            return

        entry = {
            'tag': tag,
            'value': float(value),
            'step': int(step),
            'time': datetime.now().isoformat()
        }

        with open(self.scalar_log_path, 'a', encoding='utf-8') as f:
            f.write(json.dumps(entry) + '\n')

    def get_scalars(self, tag: str) -> Dict[int, float]:
        """获取所有标量数据"""
        scalars = {}
        if not os.path.exists(self.scalar_log_path):
            return scalars

        with open(self.scalar_log_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                entry = json.loads(line)
                if entry['tag'] == tag:
                    scalars[entry['step']] = entry['value']

        return scalars
