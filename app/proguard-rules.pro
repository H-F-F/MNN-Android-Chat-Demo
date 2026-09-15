# MNN Chat Demo 混淆规则
# Demo 工程暂不开启 minify;若后续开启,需为 JNI 类保留 native 方法名
-keep class com.mnn.chatdemo.inference.MnnEngine { *; }
-keep class com.mnn.chatdemo.inference.TokenCallback { *; }
