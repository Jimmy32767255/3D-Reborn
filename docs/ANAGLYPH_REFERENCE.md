# 原版 3D Anaglyph 实现参考（1.12.2 反编译）

> 来源：`reference/decompiled-1.12.2/buq.java`（= `EntityRenderer`，1.12.2 客户端 jar 由 Vineflower 反编译）。
> 该功能在快照 **17w43a（1.13 首个快照）被彻底移除**（wiki 原文："Removed 3D Anaglyph completely."）。
> 因此 1.13 及以后版本源码中不存在该实现，需从 1.12.2 还原。

## 机制概览

Anaglyph 3D 是红-青（red/cyan）立体效果。原版做法是 **把同一个世界渲染两遍**，每遍让摄像机在 X 轴（屏幕右方向）上做轻微反向偏移，然后用颜色掩码把两遍分别只保留不同颜色通道后叠合：

| 渲染遍 | 颜色掩码 `colorMask(r,g,b,a)` | 含义 | 眼睛偏移（eye = 遍序） |
|--------|------------------------------|------|------------------------|
| pass 0 | `(false, true, true, false)` | 只保留 G+B = 青色（左眼画面） | eye=0 → 偏移 −0.07（投影）/ −0.1（视图） |
| pass 1 | `(true, false, false, false)` | 只保留 R = 红色（右眼画面）   | eye=1 → 偏移 +0.07（投影）/ +0.1（视图） |
| 恢复   | `(true, true, true, false)`  | 正常                          | — |

合成的立体画面：左眼看青色半幅，右眼看红色半幅，戴红青眼镜即得立体感。

## 关键代码（1.12.2 `buq.java`）

### 两遍渲染调度（`renderWorld` = `b(float, long)`，行 1049–1063）
```java
if (this.h.t.g) {                       // GameSettings.anaglyph
   b = 0;
   bus.a(false, true, true, false);     // colorMask: 关闭红 -> 青色遍
   this.a(0, var1, var2);               // renderPass(passIndex=0, partialTicks, nanoTime)
   b = 1;
   bus.a(true, false, false, false);    // colorMask: 只留红 -> 红色遍
   this.a(1, var1, var2);
   bus.a(true, true, true, false);      // 恢复正常 colorMask
} else {
   this.a(2, var1, var2);               // 普通单遍渲染
}
```

### 摄像机双眼偏移（`setupCameraTransform` = `a(float, int eye)`，行 559–578）
投影矩阵（GL_PROJECTION）：透视前平移
```java
float var3 = 0.07F;
if (settings.anaglyph) {
   bus.c(-(eye * 2 - 1) * 0.07F, 0.0F, 0.0F);   // translateX = -(eye*2-1)*0.07
}
... gluPerspective(...) ...
bus.n(5888);  // GL_MODELVIEW
bus.F();      // loadIdentity（重新设置视角后）
if (settings.anaglyph) {
   bus.c((eye * 2 - 1) * 0.1F, 0.0F, 0.0F);      // translateX = (eye*2-1)*0.1
}
this.d(var1); // orientCamera
```
`b(float, int eye)`（第一人称手持/手部 pass，行 617–630）同理：
```java
if (settings.anaglyph) bus.c(-(eye*2-1)*0.07F, 0, 0);   // 投影平移
... gluPerspective(...) ...
bus.n(5888); bus.F();
if (settings.anaglyph) bus.c((eye*2-1)*0.1F, 0, 0);     // 视图平移
```

### 常量总结（原版硬编码）
- 投影矩阵 X 平移系数：`0.07F`，符号 `-(eye*2-1)`
- 视图矩阵 X 平移系数：`0.1F`，符号 `(eye*2-1)`
- 眼睛索引：pass0 = 0，pass1 = 1（普通 pass = 2）
- 颜色通道：左=青(G+B)，右=红(R)

## 移植到现代的要点（26.3 / Blaze3D）
现代 Minecraft（26.1+ 未混淆）弃用原始 GL 固定管线与 `glColorMask`/`glTranslate` 组合的直接两遍叠加思路需要改用 Blaze3D 的 `PoseStack`/`Matrix4f` 与渲染目标（framebuffer）。移植策略：
1. 用 Fabric 客户端事件（世界渲染前后钩子）捕获世界渲染，分两遍以左/右摄像机偏移渲染到各自的离屏 framebuffer；
2. 第二遍以通道掩码（或后处理着色器）只保留对应颜色通道；
3. 把两遍结果相加/合成到主帧缓冲；
4. 参数（左右颜色、间距、伽玛）替代原版硬编码常量。

> 注：待 26.3 具体 API 落地后再细化（Blaze3D RenderPipeline / framebuffer 拷贝 API 名称）。
