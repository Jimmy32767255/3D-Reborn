package dev.rebof3d.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：接线渲染事件与界面。渲染相关的具体实现放在 {@code dev.rebof3d.fabric.render}。
 */
public class Reborn3DClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("rebof3d-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("3D-Reborn: client initializer loaded.");
	}
}
