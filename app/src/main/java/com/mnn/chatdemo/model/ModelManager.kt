package com.mnn.chatdemo.model

import android.content.Context
import android.content.res.AssetManager
import com.mnn.chatdemo.util.Logger
import com.mnn.chatdemo.util.Result
import java.io.File

/**
 * 模型文件管理层。
 *
 * MNN LLM 的模型是一个【目录】而非单个 .mnn 文件,llmexport 导出产物包括:
 *   config.json / llm.mnn / llm.mnn.weight / tokenizer.mtok / llm_config.json
 *   (8B 以下模型默认 tie-embedding,通常无 embeddings_bf16.bin)
 *
 * 职责:
 * 1. 首次使用时把 assets/models/qwen2-0.5b-instruct/ 整个目录拷贝到内部存储
 *    filesDir/models/qwen2-0.5b-instruct/;
 * 2. 校验关键文件(config.json / llm.mnn / tokenizer.mtok)存在;
 * 3. 返回模型【目录】的绝对路径,供 native 层 Llm::createLLM(modelDir) 使用
 *    (MNN 3.5+ 的 createLLM 接收模型目录,内部读取 config.json)。
 *
 * 懒加载:仅首次推理触发拷贝,不阻塞 Application 启动。
 */
object ModelManager {

    private const val TAG = "ModelManager"

    /** 模型目录名(与 assets 目录、内部存储目录同名) */
    const val MODEL_DIR_NAME = "qwen2-0.5b-instruct"

    /** MNN LLM 推理入口配置文件 */
    const val CONFIG_FILE = "config.json"

    /** 关键文件清单:缺失任一文件即认为模型未就绪 */
    private val REQUIRED_FILES = listOf(CONFIG_FILE, "llm.mnn", "tokenizer.mtok")

    private const val ASSET_MODEL_DIR = "models/$MODEL_DIR_NAME"

    /**
     * 确保模型就绪并返回模型目录绝对路径。
     * @return Success(模型目录绝对路径) / Failure(可展示的错误信息)
     */
    fun ensureModel(context: Context): Result<String> {
        val startNanos = System.nanoTime()
        return try {
            val destDir = File(context.filesDir, "models/$MODEL_DIR_NAME")
            copyIfNeeded(context.assets, destDir)
            verify(destDir)?.let { return Result.Failure(message = it) }

            val modelDir = destDir.absolutePath
            Logger.logElapsed(TAG, startNanos, "模型就绪: $modelDir")
            Result.Success(modelDir)
        } catch (e: Exception) {
            Logger.e(TAG, "模型准备失败", e)
            Result.Failure(e, "模型准备失败: ${e.message ?: "未知错误"}")
        }
    }

    /** 首次调用时从 assets 整目录拷贝到内部存储;已存在则跳过 */
    private fun copyIfNeeded(assets: AssetManager, destDir: File) {
        if (destDir.exists() && File(destDir, CONFIG_FILE).exists()) {
            Logger.i(TAG, "模型目录已存在,跳过拷贝: ${destDir.absolutePath}")
            return
        }

        val assetList = assets.list(ASSET_MODEL_DIR) ?: emptyArray()
        if (assetList.isEmpty()) {
            throw IllegalStateException(
                "assets/models/$MODEL_DIR_NAME 为空,请先放入模型文件(见 README「模型获取」),再重新安装 App"
            )
        }

        Logger.i(TAG, "开始从 assets 拷贝模型目录(文件数: ${assetList.size})")
        copyAssetDir(assets, ASSET_MODEL_DIR, destDir)
        Logger.i(TAG, "模型目录拷贝完成: ${destDir.absolutePath}")
    }

    /** 递归拷贝 assets 目录(区分文件与子目录) */
    private fun copyAssetDir(assets: AssetManager, assetPath: String, destDir: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // 叶子节点:按文件拷贝
            destDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                destDir.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            destDir.mkdirs()
            for (child in children) {
                copyAssetDir(assets, "$assetPath/$child", File(destDir, child))
            }
        }
    }

    /** 校验关键文件,返回缺失提示;全部存在返回 null */
    private fun verify(destDir: File): String? {
        val missing = REQUIRED_FILES.filter { !File(destDir, it).exists() }
        return if (missing.isNotEmpty()) {
            "模型目录缺少关键文件: ${missing.joinToString()} (目录: ${destDir.absolutePath})"
        } else {
            null
        }
    }
}
