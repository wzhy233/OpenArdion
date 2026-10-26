# Android Skia 渲染路径

[AndroidSkiaRenderBackend.java](../src/client/java/io/github/wzhy233/ardion/util/render/AndroidSkiaRenderBackend.java) 和 [AndroidGlBridge.java](../src/client/java/io/github/wzhy233/ardion/util/render/AndroidGlBridge.java) 负责 Android 渲染适配。

处理过程：

1. 从游戏帧缓冲读取画面。
2. 切换到 EGL 共享上下文，上传画面纹理。
3. 使用 Skia 绘制 HUD 和界面。
4. 恢复游戏上下文，将合成结果写回帧缓冲。

桌面构建说明见 [使用指南](getting-started.md)。
