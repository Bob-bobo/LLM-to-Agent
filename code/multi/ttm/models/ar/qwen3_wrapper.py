"""
Qwen3 模型包装，支持 LoRA 微调
基于 HuggingFace Transformers + PEFT
"""
import torch
import torch.nn as nn
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training


class Qwen3Wrapper:
    """
    Qwen3 大语言模型包装，使用 LoRA 进行参数高效微调
    """

    def __init__(
        self,
        base_model_name: str = "Qwen/Qwen3-7B",
        num_semantic_tokens: int = 1024,
        lora_config: dict = None,
        device: str = 'cuda',
        load_in_4bit: bool = False,
        load_in_8bit: bool = False
    ):
        """
        参数:
            base_model_name: HuggingFace 模型名称或路径
            num_semantic_tokens: 语义 token 数量
            lora_config: LoRA 配置字典，keys: rank, alpha, dropout, target_modules
            device: 计算设备
            load_in_4bit: 是否 4bit 量化加载
            load_in_8bit: 是否 8bit 量化加载
        """
        self.base_model_name = base_model_name
        self.num_semantic_tokens = num_semantic_tokens
        self.device = device

        # 默认 LoRA 配置
        if lora_config is None:
            lora_config = {
                'rank': 32,
                'alpha': 64,
                'dropout': 0.05,
                'target_modules': ["q_proj", "v_proj"]
            }

        # 加载 tokenizer
        self.tokenizer = AutoTokenizer.from_pretrained(base_model_name)
        self.tokenizer.pad_token = self.tokenizer.eos_token

        # 加载模型
        self.model = AutoModelForCausalLM.from_pretrained(
            base_model_name,
            torch_dtype=torch.bfloat16,
            load_in_4bit=load_in_4bit,
            load_in_8bit=load_in_8bit,
            device_map=device if isinstance(device, str) else 'auto',
            trust_remote_code=True
        )

        # 准备 kbit 训练
        if load_in_4bit or load_in_8bit:
            self.model = prepare_model_for_kbit_training(self.model)

        # 扩展词表：添加语义 tokens
        # 我们在原有词表后面添加 num_semantic_tokens 个新 tokens 用于语义
        self.original_vocab_size = len(self.tokenizer)
        self.semantic_start_id = self.original_vocab_size
        self.semantic_end_id = self.original_vocab_size + num_semantic_tokens - 1

        # 调整模型词表嵌入
        self.model.resize_token_embeddings(len(self.tokenizer) + num_semantic_tokens)

        # 配置 LoRA
        peft_config = LoraConfig(
            r=lora_config.get('rank', 32),
            lora_alpha=lora_config.get('alpha', 64),
            target_modules=lora_config.get('target_modules', ["q_proj", "v_proj"]),
            lora_dropout=lora_config.get('dropout', 0.05),
            bias="none",
            task_type="CAUSAL_LM"
        )

        self.model = get_peft_model(self.model, peft_config)
        self.model.print_trainable_parameters()

    def get_semantic_token_ids(self, semantic_tokens: list) -> list:
        """将 0-based 语义 token 转换为实际词表 id"""
        return [self.semantic_start_id + t for t in semantic_tokens]

    def forward(
        self,
        input_ids: torch.Tensor,
        attention_mask: torch.Tensor = None,
        labels: torch.Tensor = None
    ) -> tuple:
        """
        前向传播

        参数:
            input_ids: [B, seq_len] 输入 token ids
            attention_mask: [B, seq_len] 注意力掩码
            labels: [B, seq_len] 标签，用于计算损失

        返回:
            loss: 如果有 labels 返回损失，否则返回 logits
            logits: 输出 logits
        """
        outputs = self.model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            labels=labels,
            return_dict=True
        )
        return outputs.loss, outputs.logits if labels is not None else (None, outputs.logits)

    def generate(
        self,
        text_prompt: str,
        max_new_tokens: int,
        temperature: float = 0.8,
        top_p: float = 0.9,
        do_sample: bool = True
    ) -> torch.Tensor:
        """
        从文本提示生成语义 tokens

        参数:
            text_prompt: 输入文本描述
            max_new_tokens: 最大生成新 token 数
            temperature: 采样温度
            top_p: top-p 核采样
            do_sample: 是否采样

        返回:
            semantic_tokens: [L] 生成的语义 token ids (原始 0-based 索引)
        """
        # tokenize 文本
        encoded = self.tokenizer(
            text_prompt,
            return_tensors="pt",
            truncation=True,
            max_length=512
        )
        input_ids = encoded.input_ids.to(self.device)

        # 生成
        with torch.no_grad():
            output_ids = self.model.generate(
                input_ids=input_ids,
                max_new_tokens=max_new_tokens,
                temperature=temperature,
                top_p=top_p,
                do_sample=do_sample,
                pad_token_id=self.tokenizer.pad_token_id,
                eos_token_id=self.tokenizer.eos_token_id
            )

        # 提取生成的部分，只保留语义 tokens
        generated_ids = output_ids[0, input_ids.shape[1]:].cpu()

        # 将词表 id 转换回 0-based 语义 token 索引
        semantic_tokens = []
        for token_id in generated_ids:
            if self.semantic_start_id <= token_id <= self.semantic_end_id:
                semantic_token = token_id - self.semantic_start_id
                semantic_tokens.append(semantic_token.item())
            elif token_id == self.tokenizer.eos_token_id:
                break

        return torch.tensor(semantic_tokens, dtype=torch.long)

    def save_lora_weights(self, path: str):
        """保存 LoRA 权重"""
        self.model.save_pretrained(path)
        print(f"Saved LoRA weights to {path}")

    def load_lora_weights(self, path: str):
        """加载 LoRA 权重"""
        from peft import PeftModel
        self.model = PeftModel.from_pretrained(
            self.model,
            path,
            is_trainable=True
        )
        print(f"Loaded LoRA weights from {path}")
