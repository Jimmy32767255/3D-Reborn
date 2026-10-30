package dev.rebof3d;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 3D-Reborn —— 为现代 Minecraft 重新引入被移除的 Anaglyph 3D 立体效果。
 *
 * <p>本类为通用（main）入口，不依赖客户端渲染类，保持与加载器无关的核心逻辑可复用，
 * 便于日后移植到 NeoForge。客户端渲染/界面接线见 {@code dev.rebof3d.fabric.Reborn3DClient}。
 */
public class Reborn3D implements ModInitializer {
	public static final String MOD_ID = "rebof3d";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("3D-Reborn: common initializer loaded.");
	}
}
