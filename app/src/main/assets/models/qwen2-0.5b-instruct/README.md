# 模型说明

本目录存放 **Qwen2-0.5B-Instruct 的 MNN 预转换模型**(官方 MNN 团队转换,已量化,开箱即用)。

> 注意:模型是一个【目录】,不是单个 .mnn 文件;本目录被 .gitignore 排除,
> 不入库(体积约 532MB),下载后需重新安装 App(首次启动会把模型从 assets
> 拷贝到内部存储)。

## 文件清单

```
qwen2-0.5b-instruct/
├── config.json          ← 推理入口(backend_type=cpu / thread_num=4 / precision=low / memory=low)
├── configuration.json   ← 模型元信息
├── llm_config.json      ← 结构配置(hidden_size=896 / 24 层 / Qwen2 chat 模板)
├── llm.mnn              ← 计算图
├── llm.mnn.json         ← 算子元信息
├── llm.mnn.weight       ← 量化权重
├── embeddings_bf16.bin  ← 词嵌入(bf16)
└── tokenizer.txt        ← 分词表(旧版文本格式;新版模型为 tokenizer.mtok,二者均可)
```

## 来源

- 模型仓:ModelScope `MNN/Qwen2-0.5B-Instruct-MNN`
  https://modelscope.cn/models/MNN/Qwen2-0.5B-Instruct-MNN
- 获取命令(任一):
  ```
  git lfs install
  git clone https://modelscope.cn/models/MNN/Qwen2-0.5B-Instruct-MNN.git
  ```
  把 clone 出的目录内容(含 config.json 的一层)放入本目录。

## 注意

- App 对关键文件有存在性校验:config.json + llm.mnn 必需,分词文件 mtok/txt 二选一;
  缺失时 Snackbar 提示,不会崩溃。
