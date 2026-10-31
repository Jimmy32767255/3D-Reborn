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
 * <p>覆盖「双遍世界渲染 + GUI 离屏渲染 + 原版整屏校色叠加」整条路径，并抓取多张截图用于像素核对：
 * <ol>
 *   <li>{@code 01_inv_off}：关闭 3D 打开物品栏（颜色基准）；</li>
 *   <li>{@code 02_inv_3d_nograde}：开启 3D、界面校色关（应与 01 几乎一致，等价原版行为）；</li>
 *   <li>{@code 03_inv_3d_grade}：开启 3D、界面校色开（应按原版 c() 公式变换，R≈亮度、G/B 偏色）；</li>
 *   <li>{@code 04_inv_3d_grade_gamma}：再把伽玛调高（界面应随之变亮）；</li>
 *   <li>{@code 05_hud_3d} / {@code 06_hud_off}：HUD 3D 开/关。</li>
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

			// 开启 3D，界面校色关（应等价原版：GUI 完整彩色、与 3D 关闭几乎一致）。
			context.runOnClient(client -> withParams(p -> {
				p.enabled = true;
				p.uiColorCorrect = false;
			}));
			context.waitTicks(25);
			context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
			context.waitTicks(25);
			context.takeScreenshot("02_inv_3d_nograde");

			// 打开界面校色：GUI 应按原版 c() 公式校色（戴红青眼镜下颜色恢复）。
			context.runOnClient(client -> withParams(p -> p.uiColorCorrect = true));
			context.waitTicks(25);
			context.takeScreenshot("03_inv_3d_grade");

			// 调高伽玛：已校色的界面应随之变亮。
			context.runOnClient(client -> withParams(p -> p.gamma = 2.2f));
			context.waitTicks(25);
			context.takeScreenshot("04_inv_3d_grade_gamma");

			// 关闭界面，抓 HUD（先把伽玛复位，避免与界面校色混淆）。
			context.runOnClient(client -> withParams(p -> p.gamma = 1.0f));
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("05_hud_3d");

			context.runOnClient(client -> withParams(p -> p.enabled = false));
			context.waitTicks(15);
			context.takeScreenshot("06_hud_off");

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
