# 模型目录占位

此目录用于存放 MNN LLM 模型(**注意:模型是一个目录,不是单个 .mnn 文件**)。

## 放入内容(llmexport 导出产物)

```
qwen2-0.5b-instruct/
├── config.json          ← 推理入口(含 backend_type、thread_num、sampler 等配置)
├── llm.mnn
├── llm.mnn.weight
├── tokenizer.mtok       ← MNN 内置分词,引擎自动加载,无需额外集成分词库
├── llm_config.json
└── (可选) embeddings_bf16.bin
```

## 获取方式(任选其一)

1. **自己转换**(推荐,能讲清楚原理):
   ```
   git lfs install
   git clone https://www.modelscope.cn/qwen/Qwen2-0.5B-Instruct.git
   cd MNN/transformers/llm/export
   pip install -r requirements.txt
   python llmexport.py --path /path/to/Qwen2-0.5B-Instruct --export mnn --quant_bit 4 --hqq
   ```
   把输出目录(含 config.json 的那一层)整体拷入本目录。

2. **下载预转换模型**:从 ModelScope / HuggingFace 的 mnn-llm 相关模型仓下载 Qwen2-0.5B 的 mnn 格式模型目录。

## 注意

- 本目录已被 .gitignore 排除(模型体积 ~400MB),不要直接提交到 git;
- 模型放置后需**重新安装 App**(首次启动会把模型从 assets 拷贝到内部存储);
- App 对关键文件(config.json / llm.mnn / tokenizer.mtok)有存在性校验,缺失时提示而不是崩溃。
