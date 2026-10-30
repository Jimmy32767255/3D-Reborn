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

## 最终实现设计（已确定，m00424 更新）

### 自定义合成管线如何被编译
- `RenderPipelines.requiredPipelines()`（public）返回的列表被 `ShaderManager.reload()` 读取并逐个 `device.compilePipeline(...)`（ShaderManager.java:73-75）。
- 因此：**Mixin `RenderPipelines`，`@Inject(at=RETURN)` 到 `requiredPipelines()`**，把我们自定义的 `RenderPipeline` 追加进返回列表 → ShaderManager 会自动编译它。
- 着色器文件放 `assets/rebof3d/shaders/...`；`ShaderManager.loadConfigs` 用 `manager.listResources("shaders", ...)` 扫描**所有命名空间**（ShaderManager.java:109），故 mod 自己的命名空间着色器会被发现。无 JSON program 定义，直接 .vsh/.fsh。
- 自定义管线 = `RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)`（含 GLOBALS_SNIPPET，提供 Globals/ortho 投影）`.withVertexShader("rebof3d:core/anaglyph_screenquad").withFragmentShader("rebof3d:post/anaglyph").withBindGroupLayout(<Sampler0,Sampler1,Reborn3DConfig>).withColorTargetState(ColorTargetState.DEFAULT).withLocation("pipeline/anaglyph").build()`。
- shader 若用 `Globals` 需 `RenderSystem.bindDefaultUniforms` + setUniform("Globals", ...)；参照 PostPass.addToFrame 模式。sampler 名在绑定组里必须与 fsh 的 `uniform sampler2D Xxx;` 一致。

### 双眼渲染（Mixin GameRenderer）
- `Mixin GameRenderer`：
  - `@Redirect` 拦截 `this.levelProjectionMatrixBuffer.getBuffer(projectionMatrix)`（GameRenderer.java:661）→ 捕获最终投影 `Matrix4f`（含 bob/传送门扭曲）存字段，然后正常返回原值。
  - `@WrapOperation`（MixinExtras）拦截 `levelRenderer.render(...)` 调用（GameRenderer.java:669-671）：三维开启时：
    1. 对 eye in {0,1}：`Matrix4f m = new Matrix4f(capturedProjection).translate(StereoMath.projectionShift(eye,spacing),0,0)`；`RenderSystem.setProjectionMatrix(levelProjectionMatrixBuffer.getBuffer(m), PERSPECTIVE)`；临时把 `mainRenderTarget`（final 字段，用 `@Mutable @Accessor` 或 `@Shadow` 写）指向 left/right `MainTarget`；调用原 `render(...)`。
    2. 恢复 `mainRenderTarget` 字段与投影。
    3. 执行合成 pass：创建 `RenderPass`（color=真实 main target color view，无 depth），绑定 anaglyph 管线，setUniform "Sampler0"=left、'Sampler1'=right（近邻/线性），setUniform("Reborn3DConfig", ubo)，draw(3,1,0,0)。ubo 内容：lw.xyz+rw.xyz+gamma+saturation（用 Std140Builder 打包）。
  - `@Accessor("mainRenderTarget") @Mutable` setter，用于临时切换；或用 `@Shadow @final` + reflect。优先 @Mutable accessor mixin。
- resize：left/right target 尺寸随 main target；在 redirect 中检测尺寸变化时 resize。
- 关闭时 `@WrapOperation` 直接调用原 `render(...)`（不干预）。

### 着色器（anaglyph.fsh 逻辑）
- `vec3 L = texture(Sampler0, uv).rgb * leftWeight; vec3 R = texture(Sampler1, uv).rgb * rightWeight; vec3 c = L + R; c = pow(clamp(c,0,1), vec3(1/gamma));` 输出 `vec4(c,1)`。saturation 简单乘。与 core/StereoMath.combine 保持一致。
