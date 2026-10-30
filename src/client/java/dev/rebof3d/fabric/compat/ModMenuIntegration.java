package dev.rebof3d.fabric.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.rebof3d.fabric.gui.AnaglyphConfigScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * ModMenu 集成：在「模组」列表里为本模组提供一个配置按钮，
 * 点击后打开 {@link AnaglyphConfigScreen}。
 *
 * <p>该类仅在存在 ModMenu 时由 Fabric 通过 {@code modmenu} 入口点加载；
 * ModMenu 在编译期为 {@code compileOnly}，运行期缺失时 Fabric 只会跳过本入口点，
 * 不会影响模组本体与 F8 键位。</p>
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return (Screen parent) -> new AnaglyphConfigScreen(parent);
	}
}
