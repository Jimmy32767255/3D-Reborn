# 26.3 渲染架构调研（用于移植 anaglyph）

来源：`reference/mc263-src/`（Loom 在 26.3 上下载的客户端 jar 由 Vineflower 反编译，未混淆，官方 Mojang 名）。

## 关键结论：26.3 是帧图/提交式渲染，不是旧的立即模式

- `GameRenderer.render()` 主循环：清屏 → `preparePostEffects` → `renderLevel()` → `applyPostEffects()` → GUI。
- `GameRenderer.renderLevel()`（`net/minecraft/client/renderer/GameRenderer.java:631`）：
  - 构造 `Matrix4f projectionMatrix = new Matrix4f(cameraState.projectionMatrix)`（行 638）
  - 叠加 bob / 传送门扭曲后 `RenderSystem.setProjectionMatrix(...)`（行 661）
  - 调用 `this.minecraft.levelRenderer.render(resourcePool, renderOutline, cameraState, terrainFog, fogColor, shouldRenderSky, consistentDepthRequired)`（行 664）
  - 世界最终写入 `gameRenderer.mainRenderTarget()`。
- `LevelRenderer.render(...)`（`LevelRenderer.java:186`）内部：
  - `frame.importExternal("main", this.gameRenderer.mainRenderTarget())`（行 210）——**固定渲染到 GameRenderer.mainRenderTarget()**。
  - 用 `FrameGraphBuilder` 组织多 pass，`frame.execute(...)` 提交。
- `GameRenderer.mainRenderTarget()`（行 818）返回 `this.mainRenderTarget`（`private final RenderTarget`，MainTarget，行 179）。

## Post 后处理机制（可复用来做合成）

- Post effect 由 JSON 定义：`assets/minecraft/post_effect/*.json`（如 `invert.json`）。结构：`targets` + `passes[]`，每个 pass 指定 `vertex_shader`（`minecraft:core/screenquad`）、`fragment_shader`、`inputs`（`sampler_name` + `target`）、`output`、`uniforms`。
- 片元着色器 GLSL 330：标准 `uniform sampler2D InSampler;`、`layout(std140) uniform XxxConfig { ... }`、`in` texCoord、`out vec4 fragColor`。样例见 `assets/minecraft/shaders/post/blit.fsh`、`invert.fsh`、`core/screenquad.vsh`。
- `mix`、`texture(InSampler, texCoord)` 等标准用法可用。uniform 块名与 JSON `uniforms` 键对应。

## 投影矩阵（复刻原版眼睛偏移的落点）

- 原版 1.12.2：`projection.translateX(-(eye*2-1)*0.07)`，`modelView.translateX((eye*2-1)*0.1)`。
- 26.3 中投影在 `GameRenderer.renderLevel()` 行 638 构造；等价做法：`projectionMatrix.translate(eyeOffsetX, 0, 0)`（joml Matrix4f.translate）。modelView 层：`LevelRenderer.render` 内 `modelViewStack.mul(cameraState.viewRotationMatrix)`（行 ~199），可在 `CameraRenderState` 上做平移，或直接在投影上做偏移（更简单、视觉等价于原版投影偏移项）。
- `CameraRenderState.projectionMatrix`（`net/minecraft/client/renderer/state/level/CameraRenderState.java`）为 Matrix4f。

## 可行实现路径（忠实：双遍渲染取不同眼睛 → 通道合成）

**方案（选定）**：Mixin `GameRenderer.renderLevel()`，用 `@Redirect` 拦截 `levelRenderer.render(...)` 的调用：
1. 准备两个离屏 `RenderTarget`（left/right），尺寸同主 target。
2. 眼睛 0（左，取 G+B=青）：临时把 `GameRenderer.mainRenderTarget`（final 字段，用 mixin `@Mutable @Accessor` 或 `@Shadow` 直接写）指向 left target；把 `projectionMatrix` 左移 `-(eye*2-1)*spacing`；调用原 `levelRenderer.render`。
3. 眼睛 1（右，取 R=红）：切到 right target，投影右移；再渲染一遍。
4. 恢复 `mainRenderTarget`。
5. 合成 pass：自绘 pipeline/着色器，采样 left+right 两张纹理，按 `R(right)+GB(left)` 组合，套用可调「左/右滤光片颜色、间距、gamma 校正」uniform，输出到主 target。
   - 或在 `renderLevel` 末尾追加一个内部 post pass 到主 target。

**风险点**：
- `mainRenderTarget` 为 `private final`——写它需 `@Mutable` accessor mixin（可行）。
- `LevelRenderer.render` 内部 framegraph `importExternal("main", gameRenderer.mainRenderTarget())` 会读取当前字段值 → 只要在调用前换掉字段即可让它渲染进我们的离屏 target。这是关键可乘之机。
- 26.2+ 有实验性 Vulkan 后端；合成自绘 pipeline 需走 Blaze3D `RenderPipeline` 抽象而非裸 GL（否则 Vulkan 失效；OpenGL 下仍可）。
- 需自建 `RenderTarget`（有 depth+color），并在 resize 时重建。

## 里程碑拆分（建议）
1. 纯数学/配置核心（不依赖 MC）：eyeOffset、颜色、gamma —— 可顺手单元验证。
2. Mixin 骨架 + 双遍渲染到离屏 target（先输出「左右并排」或「只左眼」验证拦截生效）。
3. 合成着色器（先固定红青），验证立体效果。
4. 参数接入 + gamma。
5. GUI 调参界面（Cloth-Config 风格自绘，或直接自绘 Screen）。
6. 配置持久化（简单 JSON）。
