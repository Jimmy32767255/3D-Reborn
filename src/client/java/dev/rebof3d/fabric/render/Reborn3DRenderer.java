package dev.rebof3d.fabric.render;

import dev.rebof3d.core.AnaglyphConfig;
import dev.rebof3d.core.AnaglyphParams;

/**
 * 客户端渲染运行时状态持有者：单例 {@link AnaglyphRenderer} 与当前生效的参数。
 * 由客户端初始化与配置读写更新；mixin 从这里读取当前参数。
 */
public final class Reborn3DRenderer {

	private static final AnaglyphRenderer RENDERER = new AnaglyphRenderer();

	private static AnaglyphConfig config = new AnaglyphConfig();

	private Reborn3DRenderer() {
	}

	public static AnaglyphRenderer renderer() {
		return RENDERER;
	}

	/** 当前参数（可能为 null 表示尚未初始化）。 */
	public static AnaglyphParams params() {
		return config != null ? config.getParams() : null;
	}

	public static AnaglyphConfig config() {
		return config;
	}

	public static void setConfig(AnaglyphConfig newConfig) {
		config = newConfig != null ? newConfig : new AnaglyphConfig();
	}
}
