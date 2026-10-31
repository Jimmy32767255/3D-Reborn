# 3D-Reborn

Re-introduces Minecraft's **3D Anaglyph** stereoscopic effect — removed in snapshot
**17w43a** (the 1.13 cycle) — as a lightweight **Fabric** mod, targeting modern
versions (first target: **26.3**).

在原版红/青 anaglyph 的基础上，额外提供可调参数：**左右滤光片颜色**、**两摄像机间距**、
**伽玛校准**、颜色饱和度，并通过游戏内界面实时调整。

## 功能

- 以「左右眼各渲染一次，再按滤光片颜色合成」的方式还原原版 3D 立体效果。
- **HUD / GUI 界面颜色校准**：界面不再逐眼渲染，而是整幅绘制到一张透明底离屏图层，
  再按原版 1.12.2 的整屏校色公式（亮度→R、偏红→G、偏青→B，即 `cdt.c()`）处理后
  按 alpha 叠加回已合成的世界。这样佩戴红青眼镜时界面亮度与颜色恢复正常观感，
  且统一受伽玛 / 饱和度参数影响（默认开启，可在设置中关闭以恢复原版观感）。
- 游戏内设置界面（默认快捷键 **F8**）调整：
  - 开关立体效果
  - 摄像机间距（立体强度）
  - 左眼 / 右眼 滤光片颜色（RGB）
  - 伽玛校准
  - 颜色饱和度
  - 界面颜色校准（开关）
- 参数自动保存至 `config/rebof3d.json`，重启后保留。
- 若安装了 [ModMenu](https://modrinth.com/mod/modmenu)，「模组」列表中可直接点击配置按钮打开同一设置界面（可选依赖）。
- 未启用时零额外开销：渲染路径完全短路，不影响正常游玩。

## 构建

需要 **JDK 25**（26.3 的 class 版本为 Java 25）。

```bash
./gradlew build
```

产物：`build/libs/3d-reborn-0.1.0.jar`。

> 说明：26.3 的 Loom 采用「无重映射」(mojmap) 方案，运行时直接用 Mojang 官方名；
> 因此本模组是面向 26.3 的真实 Fabric 工程，而非旧版映射工程。

## 安装

1. 安装对应 **26.3** 的 Fabric Loader 与 **Fabric API**。
2. 将 `build/libs/3d-reborn-0.1.0.jar` 放入 `.minecraft/mods/`。
3. 启动游戏，按 **F8** 打开设置界面，勾选「启用」即可。

> 可选：同时安装 [ModMenu](https://modrinth.com/mod/modmenu)（21.0.0，对应 26.3）后，
> 「模组」列表中本模组会多出齿轮/配置按钮，可直接打开设置界面。ModMenu 为**可选**依赖，
> 未安装时仅影响该入口，F8 快捷键与立体效果照常工作（本模组不会打包或强制依赖 ModMenu）。

## 参数说明

| 参数 | 含义 | 默认 |
| --- | --- | --- |
| 启用 | 是否开启立体效果 | 关 |
| 摄像机间距 | 两眼视角差强度（越大越立体） | 1.0 |
| 左眼滤光片 | 左眼图像通道权重（RGB） | 青 (0,1,1) |
| 右眼滤光片 | 右眼图像通道权重（RGB） | 红 (1,0,0) |
| 伽玛校准 | `pow(color, 1/gamma)` 中间调校正 | 1.0 |
| 饱和度 | 每眼图像颜色饱和度增益 | 1.0 |
| 界面颜色校准 | 对 GUI/HUD 做原版整屏校色（戴红青眼镜时界面颜色恢复正常） | 开 |

佩戴红/青（red-cyan）立体眼镜观看效果最佳；也可自行调整滤光片颜色以适配其他眼镜。

## 架构与可移植性

- `dev.rebof3d.core.*`：与加载器（Fabric / 未来的 NeoForge）完全解耦的参数模型、
  双眼偏移数学、JSON 配置逻辑，不含任何 Minecraft / Fabric 类型，可原样复用。
- `dev.rebof3d.fabric.*`：Fabric 侧的渲染实现、Mixin 与界面；未来新增 NeoForge 模块时
  只需替换这一层。

## 已知限制

- **实体发光描边（entity outline）与部分屏幕后处理**在立体模式下可能丢失：立体渲染会在世界
  两遍渲染后合成一次，随后 GUI 图层校色后以 alpha 叠加回该成品，此叠加会覆盖原版在世界之后写入
  主目标的描边 / 后处理结果。常规游玩（无发光实体、无自定义后处理）不受影响。
- 未来的 **NeoForge** 版本属于独立阶段：`core` 层已完全解耦，NeoForge 仅需另写渲染 / Mixin 适配层。

## 许可证

**GPLv3 Only**。见 `LICENSE`。
