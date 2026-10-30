package dev.rebof3d.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rebof3d.fabric.config.ClientConfig;
import dev.rebof3d.fabric.gui.AnaglyphConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：加载配置、注册打开设置界面的按键、驱动界面打开。
 * 渲染相关的具体实现放在 {@code dev.rebof3d.fabric.render}。
 */
public class Reborn3DClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("rebof3d-client");

	private static KeyMapping openSettingsKey;

	@Override
	public void onInitializeClient() {
		ClientConfig.load();

		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.rebof3d.settings",
			InputConstants.KEY_F8,
			new KeyMapping.Category(Identifier.fromNamespaceAndPath("rebof3d", "main"))
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openSettingsKey.consumeClick()) {
				openSettingsScreen(client);
			}
		});

		LOGGER.info("3D-Reborn: client initializer loaded.");
	}

	private static void openSettingsScreen(Minecraft client) {
		client.setScreenAndShow(new AnaglyphConfigScreen(client.gui.screen()));
	}
}
