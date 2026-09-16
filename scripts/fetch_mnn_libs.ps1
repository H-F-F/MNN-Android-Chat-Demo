# fetch_mnn_libs.ps1
# 自动获取 MNN 预编译库(官方 MNNChat Android APK -> libMNN.so)
#
# 背景:官方 MNNChat App 的 libMNN.so 以 -DMNN_BUILD_LLM=ON 编译,
# LLM 模块内置其中(无独立 libllm.so),与本工程 CMake 配置匹配。
#
# 用法(任选):
#   1. 右键本文件 -> 使用 PowerShell 运行
#   2. powershell -ExecutionPolicy Bypass -File scripts\fetch_mnn_libs.ps1
#
# 输出:app/src/main/jniLibs/arm64-v8a/libMNN.so

$ErrorActionPreference = "Stop"

$root      = Split-Path -Parent $PSScriptRoot
$apkUrl    = "https://meta.alicdn.com/data/mnn/apks/mnn_chat_0_8_3.apk"
$apk       = Join-Path $env:TEMP "mnn_chat_0_8_3.apk"
$extract   = Join-Path $env:TEMP "mnn_chat_extract"
$dest      = Join-Path $root "app\src\main\jniLibs\arm64-v8a"

New-Item -ItemType Directory -Force -Path $dest, $extract | Out-Null

Write-Host "[1/3] 下载官方 MNNChat APK(引擎 3.5.x)..."
curl.exe -L -o $apk $apkUrl
if (-not (Test-Path $apk)) { throw "APK 下载失败: $apkUrl" }

Write-Host "[2/3] 解压提取 libMNN.so ..."
tar -xf $apk -C $extract "lib/arm64-v8a/libMNN.so"
$so = Join-Path $extract "lib\arm64-v8a\libMNN.so"
if (-not (Test-Path $so)) { throw "APK 中未找到 libMNN.so" }

Write-Host "[3/3] 拷贝到工程 jniLibs ..."
Copy-Item $so (Join-Path $dest "libMNN.so") -Force
Remove-Item $apk, $extract -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "完成: $dest\libMNN.so"
