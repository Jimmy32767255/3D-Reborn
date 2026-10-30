package dev.rebof3d.core;

/**
 * 双眼偏移的纯数学计算。与原版 1.12.2 {@code EntityRenderer.renderWorld} 中的做法对应：
 *
 * <pre>
 * // 原版（每条眼睛 eye=0 左, eye=1 右）
 * projection.translate(-(eye * 2 - 1) * 0.07, 0, 0);  // 投影矩阵平移
 * modelView.translate((eye * 2 - 1) * 0.1, 0, 0);     // 视图矩阵平移
 * </pre>
 *
 * <p>现代渲染中我们只对「投影矩阵」施加水平平移即可获得视觉等价的双眼视差
 * （view 平移等价于在投影后产生相同的水平位移方向，见 docs/RENDERING_263_RESEARCH.md）。
 * {@code cameraSpacing} 作为整体缩放系数。
 *
 * <p>本类不依赖 MC，可独立测试。
 */
public final class StereoMath {

	/** 原版投影平移基准常量 0.07。 */
	public static final float BASE_PROJECTION_SHIFT = 0.07f;

	private StereoMath() {
	}

	/**
	 * 返回某只眼睛的投影矩阵水平平移量（传给 joml {@code Matrix4f.translate(x,0,0)}）。
	 *
	 * @param eye           眼睛索引：0 = 左眼，1 = 右眼（与原版一致）
	 * @param cameraSpacing 间距缩放系数
	 */
	public static float projectionShift(int eye, float cameraSpacing) {
		// eye=0 -> eye*2-1 = -1 -> +0.07*spacing（左眼整体偏左）
		// eye=1 -> eye*2-1 = +1 -> -0.07*spacing（右眼整体偏右）
		return -(eye * 2 - 1) * BASE_PROJECTION_SHIFT * cameraSpacing;
	}

	/**
	 * 把雾色/清屏色修正为「红青对称」的颜色，对应原版 1.12.2 {@code EntityRenderer}（{@code buq.java:1724-1733}）：
	 *
	 * <pre>
	 * if (anaglyph) {
	 *    float lum = (R*30 + G*59 + B*11) / 100;  // 亮度 → R
	 *    float rg  = (R*30 + G*70) / 100;         // 偏红 → G
	 *    float rb  = (R*30 + B*70) / 100;         // 偏青 → B
	 *    R = lum; G = rg; B = rb;
	 * }
	 * </pre>
	 *
	 * <p>立体模式下左眼只能看到红通道、右眼只能看到青通道，未修正的雾色会让两侧亮度失衡
	 * （天空/远景一侧偏亮、一侧偏暗）。原版据此把雾色重算为「亮度 + 红青偏色」的组合，
	 * 使整屏底色在两只眼中观感一致。这也是原版唯一一处针对立体模式的整屏颜色校正。
	 *
	 * <p>本类不依赖 MC，可独立测试。
	 */
	public static float[] correctFogColor(float r, float g, float b) {
		float[] out = new float[3];
		correctFogColor(r, g, b, out);
		return out;
	}

	/** 就地修正雾色，避免每帧分配数组。语义同 {@link #correctFogColor(float, float, float)}。 */
	public static void correctFogColor(float r, float g, float b, float[] out) {
		out[0] = (r * 30.0f + g * 59.0f + b * 11.0f) / 100.0f;
		out[1] = (r * 30.0f + g * 70.0f) / 100.0f;
		out[2] = (r * 30.0f + b * 70.0f) / 100.0f;
	}

	/**
	 * 左眼通道权重（RGB）。原版固定「左=青(0,1,1)、右=红(1,0,0)」，此处允许用户自定义。
	 */
	public static float[] leftWeight(AnaglyphParams p) {
		return new float[] { p.leftFilterR, p.leftFilterG, p.leftFilterB };
	}

	/** 右眼通道权重（RGB）。 */
	public static float[] rightWeight(AnaglyphParams p) {
		return new float[] { p.rightFilterR, p.rightFilterG, p.rightFilterB };
	}

	/**
	 * CPU 侧参考合成：把左右眼颜色按滤光片权重合成并做伽玛/饱和度校正。
	 * 仅用于测试与对照；实际渲染由 GLSL 着色器完成，逻辑与此保持一致。
	 *
	 * @param leftColor  左眼颜色 [r,g,b]，0..1
	 * @param rightColor 右眼颜色 [r,g,b]，0..1
	 * @return 合成后颜色 [r,g,b]
	 */
	public static float[] combine(AnaglyphParams p, float[] leftColor, float[] rightColor) {
		float[] lw = leftWeight(p);
		float[] rw = rightWeight(p);
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			float l = applySaturationOne(p, leftColor[i]);
			float r = applySaturationOne(p, rightColor[i]);
			out[i] = l * lw[i] + r * rw[i];
		}
		// 伽玛校正
		float invGamma = p.gamma != 0f ? 1f / p.gamma : 1f;
		for (int i = 0; i < 3; i++) {
			out[i] = (float) Math.pow(clamp01(out[i]), invGamma);
		}
		return out;
	}

	// 单通道饱和度处理（简化：与通道均值混合）。与着色器保持一致的近似。
	private static float applySaturationOne(AnaglyphParams p, float c) {
		if (p.saturation == 1.0f) {
			return c;
		}
		return c * p.saturation;
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}
