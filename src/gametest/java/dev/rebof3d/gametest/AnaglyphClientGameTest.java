package dev.rebof3d.gametest;

import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * 客户端游戏内冒烟测试：真实启动客户端、新建单人存档，在世界渲染期启用 3D 立体效果。
 *
 * <p>覆盖「双遍世界渲染 + GUI 逐眼渲染 + 两次合成」整条路径，并抓取多张截图用于像素核对：
 * <ol>
 *   <li>关闭 3D 打开物品栏（作为颜色基准 {@code 01_inv_off}）；</li>
 *   <li>开启 3D 打开物品栏（{@code 02_inv_3d}——GUI 应恢复完整彩色，不被通道抢走）；</li>
 *   <li>开启 3D 且调高伽玛后再次截图（{@code 03_inv_3d_gamma}——GUI 应随伽玛变亮）；</li>
 *   <li>HUD（关闭界面）3D 开/关（{@code 04_hud_3d} / {@code 05_hud_off}）。</li>
 * </ol>
 *
 * <p>不写像素断言，只要不抛异常且能跑到测试结束；具体颜色由外部脚本比对截图。
 */
public class AnaglyphClientGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);

			// 基准：3D 关闭，打开物品栏。
			context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
			context.waitTicks(25);
			context.takeScreenshot("01_inv_off");
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(5);

			// 开启 3D，打开物品栏。
			context.runOnClient(client -> withParams(p -> p.enabled = true));
			context.waitTicks(25);
			context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
			context.waitTicks(25);
			context.takeScreenshot("02_inv_3d");

			// 调高伽玛：GUI 也应随之变化（证明 GUI 已纳入统一校色，而非合成后独立绘制）。
			context.runOnClient(client -> withParams(p -> p.gamma = 2.2f));
			context.waitTicks(25);
			context.takeScreenshot("03_inv_3d_gamma");

			// 关闭界面，抓 HUD。
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("04_hud_3d");

			context.runOnClient(client -> withParams(p -> p.enabled = false));
			context.waitTicks(15);
			context.takeScreenshot("05_hud_off");

			// 断言合成管线确实执行过（离屏目标仅由 composite()->ensureTargets() 创建）。
			context.runOnClient(client -> {
				if (Reborn3DRenderer.renderer().eyeTarget(0) == null) {
					throw new AssertionError("3D 已启用但合成管线未执行：左右眼离屏目标未被创建");
				}
			});
		}
	}

	private static void withParams(java.util.function.Consumer<AnaglyphParams> mutator) {
		AnaglyphParams params = Reborn3DRenderer.params();
		if (params != null) {
			mutator.accept(params);
		}
	}
}
