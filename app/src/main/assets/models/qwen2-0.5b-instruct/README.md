# 模型文件说明

本目录下的模型文件体积较大(约 532MB),**已被 .gitignore 忽略,不会入库**。
克隆仓库后需要手动下载模型才能运行。

## 模型信息

| 项 | 值 |
| --- | --- |
| 模型 | Qwen2-0.5B-Instruct(MNN 预转换版) |
| 来源 | ModelScope: `MNN/Qwen2-0.5B-Instruct-MNN` |
| 转换版本 | MNN 3.0.2 |
| 量化 | 权重 INT8(内存 low,精度 low) |
| 用途 | 端侧离线对话演示(手机 CPU 可实时推理) |

## 下载方式

将以下文件放入本目录(文件名必须一致):

```
config.json
configuration.json
llm_config.json
tokenizer.txt
llm.mnn
llm.mnn.json
llm.mnn.weight
embeddings_bf16.bin
```

ModelScope 单文件下载地址模板:

```
https://modelscope.cn/api/v1/models/MNN/Qwen2-0.5B-Instruct-MNN/repo?Revision=master&FilePath=<文件名>
```

大文件(如 `llm.mnn.weight`)会返回 302 跳转,需解析响应中的 `href` 再下载。

## 为什么选 0.5B

在这台中端手机(8 核 CPU)上实测:

- **Qwen2-0.5B**:首次加载约 20s,热后单条回复 1~6 秒 —— 可用
- **Qwen2-1.5B**:内容更稳定,但 CPU 仅 1~2 token/秒,单条回复 2 分钟+ —— 物理不可用

这是端侧推理"速度 vs 质量"的经典权衡,详见项目 README 的调优记录。
