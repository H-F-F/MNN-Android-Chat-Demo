# mnn/include 占位

把 **MNN 仓库的 include/ 目录内容**整体拷入本目录,例如:

```
mnn/include/
├── llm/
│   └── llm.hpp          ← LLM 模块头文件(2.8 主仓路径)
├── MNN/                 ← 基础头文件(llm.hpp 会引用)
│   ├── Interpreter.hpp
│   └── ...
└── ...
```

版本说明:
- 新版 MNN 主仓头文件路径为 `llm/llm.hpp`,命名空间 `MNN::LLM`;
- 老版 mnn-llm 独立仓为 `llm.hpp`,命名空间 `MNN`;
- `mnn_inference.cpp` 顶部已做好版本适配注释,按实际下载的头文件微调即可。

本目录已被 .gitignore 排除(MNN 头文件属于第三方源码,不建议入库)。
