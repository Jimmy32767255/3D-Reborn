package dev.rebof3d.fabric.config;

import dev.rebof3d.core.AnaglyphConfig;
import dev.rebof3d.fabric.Reborn3DClient;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * 客户端配置的加载/保存。文件位于 {@code <gameDir>/config/rebof3d.json}。
 * 真正的读写逻辑在加载器无关的 {@link AnaglyphConfig} 中。
 */
public final class ClientConfig {

	private ClientConfig() {
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("rebof3d.json");
	}

	/** 从磁盘加载并安装到运行时状态；失败时保留默认值并记录日志。 */
	public static void load() {
		AnaglyphConfig cfg = AnaglyphConfig.load(path());
		if (cfg.getLoadError() != null) {
			Reborn3DClient.LOGGER.warn("3D-Reborn: failed to read config {}, using defaults.", path(), cfg.getLoadError());
		} else {
			Reborn3DClient.LOGGER.info("3D-Reborn: loaded config from {}.", path());
		}
		Reborn3DRenderer.setConfig(cfg);
	}

	/** 把当前运行时状态写回磁盘。 */
	public static void save() {
		try {
			Reborn3DRenderer.config().save(path());
		} catch (Exception e) {
			Reborn3DClient.LOGGER.error("3D-Reborn: failed to save config to {}.", path(), e);
		}
	}
}
