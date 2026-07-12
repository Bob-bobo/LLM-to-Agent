"""
AR 自回归模型主类
基于 Qwen3 生成语义 tokens
"""
import torch
import torch.nn as nn
from typing import Optional
from .qwen3_wrapper import Qwen3Wrapper


class ARQwen3(nn.Module):
    """
    自回归文本→语义 token 生成模型
    基于预训练 Qwen3，使用 LoRA 微调
    """

    def __init__(
        self,
        base_model_name: str = "Qwen/Qwen3-7B",
        num_semantic_tokens: int = 1024,
        max_sequence_length: int = 2048,
        lora_enable: bool = True,
        lora_rank: int = 32,
        lora_alpha: int = 64,
        lora_dropout: float = 0.05,
        lora_target_modules: list = None,
        device: str = 'cuda',
        load_in_4bit: bool = False,
        load_in_8bit: bool = False
    ):
        super().__init__()
        self.num_semantic_tokens = num_semantic_tokens
        self.max_sequence_length = max_sequence_length

        if lora_target_modules is None:
            lora_target_modules = ["q_proj", "v_proj"]

        lora_config = {
            'rank': lora_rank,
            'alpha': lora_alpha,
            'dropout': lora_dropout,
            'target_modules': lora_target_modules
        }

        self.qwen_wrapper = Qwen3Wrapper(
            base_model_name=base_model_name,
            num_semantic_tokens=num_semantic_tokens,
            lora_config=lora_config if lora_enable else None,
            device=device,
            load_in_4bit=load_in_4bit,
            load_in_8bit=load_in_8bit
        )

        self.device = device

    def forward(
        self,
        text: list[str],
        semantic_target: list[torch.Tensor]
    ) -> torch.Tensor:
        """
        前向传播计算损失

        输入格式：
            <text>BOS 文本描述 EOS semantic_token_1 semantic_token_2 ... semantic_token_n EOS

        参数:
            text: 批量文本描述
            semantic_target: 每个文本对应的目标语义 token 列表

        返回:
            loss: 平均交叉熵损失
        """
        # 构建完整输入序列：文本 + 语义 tokens
        batch_inputs = []
        batch_labels = []

        for i, desc in enumerate(text):
            # tokenize 文本
            desc_tokens = self.qwen_wrapper.tokenizer.encode(
                desc,
                add_special_tokens=True
            )

            # 获取语义 token 的词表 id
            target_tokens = semantic_target[i]
            semantic_ids = self.qwen_wrapper.get_semantic_token_ids(
                target_tokens.tolist()
            )

            # 完整序列：文本 + 语义 tokens + EOS
            full_seq = desc_tokens + semantic_ids + \
                [self.qwen_wrapper.tokenizer.eos_token_id]

            # labels: 文本部分设为 -100（不计算损失），只计算语义 token 部分损失
            labels = [-100] * len(desc_tokens) + semantic_ids + \
                [self.qwen_wrapper.tokenizer.eos_token_id]

            # 截断到最大长度
            if len(full_seq) > self.max_sequence_length:
                full_seq = full_seq[:self.max_sequence_length]
                labels = labels[:self.max_sequence_length]

            batch_inputs.append(full_seq)
            batch_labels.append(labels)

        # padding 到最长序列
        max_len = max(len(seq) for seq in batch_inputs)
        input_ids = []
        attention_mask = []
        labels_padded = []

        pad_id = self.qwen_wrapper.tokenizer.pad_token_id
        for seq, labs in zip(batch_inputs, batch_labels):
            padding_len = max_len - len(seq)
            input_ids.append(seq + [pad_id] * padding_len)
            attention_mask.append([1] * len(seq) + [0] * padding_len)
            labels_padded.append(labs + [-100] * padding_len)

        input_ids = torch.tensor(input_ids, dtype=torch.long, device=self.device)
        attention_mask = torch.tensor(attention_mask, dtype=torch.long, device=self.device)
        labels = torch.tensor(labels_padded, dtype=torch.long, device=self.device)

        # 前向计算损失
        loss, _ = self.qwen_wrapper.forward(input_ids, attention_mask, labels)
        return loss

    def generate(
        self,
        text_prompt: str,
        max_tokens: int,
        temperature: float = 0.8,
        top_p: float = 0.9
    ) -> torch.Tensor:
        """
        自回归生成语义 tokens

        参数:
            text_prompt: 文本描述
            max_tokens: 最大生成语义 token 数
            temperature: 采样温度
            top_p: top-p 采样

        返回:
            semantic_tokens: [L] 生成的语义 tokens (0-based)
        """
        self.eval()
        with torch.no_grad():
            tokens = self.qwen_wrapper.generate(
                text_prompt=text_prompt,
                max_new_tokens=max_tokens,
                temperature=temperature,
                top_p=top_p,
                do_sample=temperature > 0
            )
        return tokens

    def save(self, save_path: str):
        """保存 LoRA 权重"""
        self.qwen_wrapper.save_lora_weights(save_path)

    def load(self, load_path: str):
        """加载 LoRA 权重"""
        self.qwen_wrapper.load_lora_weights(load_path)
