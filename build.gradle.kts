// 根构建脚本:统一声明插件版本
// 注意:JDK 21 环境下 Gradle wrapper 需 >= 8.5(本工程使用 8.7)
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
