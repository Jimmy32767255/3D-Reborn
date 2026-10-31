package dev.rebof3d.core;

/**
 * Anaglyph 3D 的可调参数模型。**刻意不依赖任何 Minecraft 类型**，以便：
 * <ul>
 *   <li>核心逻辑可单元测试；</li>
 *   <li>日后移植到 NeoForge 时原样复用（两个加载器共享同一份 core 源集）。</li>
 * </ul>
 *
 * <p>字段语义与原版 1.12.2 {@code EntityRenderer} 的 anaglyph 实现保持一致，并在此基础上
 * 增加了左右滤光片颜色、伽玛校准、颜色饱和度等可调项。
 */
public final class AnaglyphParams {

	/** 是否启用 3D 立体效果。 */
	public boolean enabled = false;

	/**
	 * 两个虚拟摄像机间距的缩放系数（原版硬编码为 {@code 0.1} 的 modelView 平移与
	 * {@code 0.07} 的投影平移）。数值越大，左右眼视角差越大，立体感越强。
	 */
	public float cameraSpacing = 1.0f;

	/**
	 * 左眼滤光片颜色（RGB，0..1）。默认青色 (0,1,1)，即只让绿+蓝通过，红被滤掉。
	 * 这是给「左眼图像」乘上的通道权重。
	 *
	 * <p>与原版 1.12.2 anaglyph 一致：eye 0（左眼）的 colorMask 只打开 G+B（青），
	 * eye 1（右眼）只打开 R（红）。对应通用「红-青」立体眼镜（红片在右眼、青片在左眼）。
	 */
	public float leftFilterR = 0.0f;
	public float leftFilterG = 1.0f;
	public float leftFilterB = 1.0f;

	/**
	 * 右眼滤光片颜色（RGB，0..1）。默认红色 (1,0,0)，即只让红通过。
	 */
	public float rightFilterR = 1.0f;
	public float rightFilterG = 0.0f;
	public float rightFilterB = 0.0f;

	/**
	 * 伽玛校准。对合成后的颜色做 {@code pow(color, 1/gamma)} 校正。
	 * 1.0 为不校正；a>1 提亮中间调，<1 压暗。默认 1.0。
	 */
	public float gamma = 1.0f;

	/**
	 * 每个眼睛图像的颜色饱和度增益。1.0 保持原色，>1 更鲜艳，0 变灰。
	 * 配合滤光片颜色可微调观感。默认 1.0。
	 */
	public float saturation = 1.0f;

	/**
	 * 是否对界面（GUI / HUD）做 anaglyph 色彩校准。默认开启（「开箱即用」）。
	 *
	 * <p>开启后，界面会按原版 1.12.2 对纹理的校色公式（亮度→R、偏红→G、偏青→B）
	 * 变换并叠加全局伽玛/饱和度，使佩戴红青眼镜时界面亮度与颜色恢复正常观感，
	 * 无需手动调整左右滤光片颜色。
	 */
	public boolean uiColorCorrect = true;

	public AnaglyphParams copy() {
		AnaglyphParams c = new AnaglyphParams();
		c.enabled = enabled;
		c.cameraSpacing = cameraSpacing;
		c.leftFilterR = leftFilterR;
		c.leftFilterG = leftFilterG;
		c.leftFilterB = leftFilterB;
		c.rightFilterR = rightFilterR;
		c.rightFilterG = rightFilterG;
		c.rightFilterB = rightFilterB;
		c.gamma = gamma;
		c.saturation = saturation;
		c.uiColorCorrect = uiColorCorrect;
		return c;
	}
}
