package dev.rebof3d.gametest;

import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * 客户端游戏内冒烟测试：真实启动客户端、新建单人存档，在世界渲染期启用 3D 立体效果并
 * 连续推进多帧，覆盖「双遍渲染 + 合成」路径（{@code AnaglyphRenderer.composite}）。
 *
 * <p>该路径历史上曾在第二帧因配置 UBO 缺少 {@code USAGE_COPY_DST} 而崩溃；本测试确保：
 * <ol>
 *   <li>首帧 {@code createBuffer} 建 UBO、绑定描述符并 {@code draw} 成功；</li>
 *   <li>后续帧走 {@code writeToBuffer} 回写路径不再抛 {@code IllegalStateException}；</li>
 *   <li>运行期修改参数（间距/伽玛）不引发异常。</li>
 * </ol>
 *
 * <p>不写任何断言，只要不抛异常且能跑到测试结束，即代表整条合成管线在真实世界里可正常工作。
 */
public class AnaglyphClientGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			// 等待世界创建完成、进入游戏画面并稳定若干帧。
			context.waitTicks(60);

			// 启用 3D 效果（触发双遍渲染 + 合成管线）。
			context.runOnClient(client -> withParams(p -> p.enabled = true));

			// 推进多帧：覆盖首帧 createBuffer 与第二帧起的 writeToBuffer 回写路径。
			context.waitTicks(80);

			// 断言合成管线确实执行过：离屏眼睛目标只会在 composite() -> ensureTargets() 中创建。
			// 若某次回归导致世界渲染期不再走合成路径，这里会失败（而不是静默「没崩就算过」）。
			context.runOnClient(client -> {
				if (Reborn3DRenderer.renderer().eyeTarget(0) == null) {
					throw new AssertionError("3D 已启用但合成管线未执行：左右眼离屏目标未被创建");
				}
			});

			// 模拟在界面中调整参数：改变摄像机间距与伽玛。
			context.runOnClient(client -> withParams(p -> {
				p.cameraSpacing = 2.0f;
				p.gamma = 1.2f;
				p.leftFilterR = 0.1f;
				p.rightFilterB = 0.1f;
			}));
			context.waitTicks(60);

			// 关闭 3D，确保直通路径同样正常。
			context.runOnClient(client -> withParams(p -> p.enabled = false));
			context.waitTicks(20);
		}
	}

	private static void withParams(java.util.function.Consumer<AnaglyphParams> mutator) {
		AnaglyphParams params = Reborn3DRenderer.params();
		if (params != null) {
			mutator.accept(params);
		}
	}
}
