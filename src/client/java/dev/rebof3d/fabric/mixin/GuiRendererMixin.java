package dev.rebof3d.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.fabric.bridge.MainTargetAccess;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让 GUI（物品栏、血条、屏幕效果等）也参与 anaglyph 立体编码。
 *
 * <p><b>为什么必须逐眼绘制 GUI：</b>原版 GUI 在 {@code renderLevel()} 之后单独全彩绘制一次。
 * 立体合成公式为 {@code out = L_world*leftFilter + R_world*rightFilter}，GUI 不含在其中，
 * 于是戴红青眼镜时 GUI 的 R 通道只进右眼、G/B 只进左眼 → 通道被眼镜抢走、发暗偏色。
 * 若把 GUI 分别画进左右眼图再合成，则
 * {@code out = world*(lf+rf) + gui*(lf+rf) = world + gui}，GUI 重新变成完整彩色，
 * 且 gamma/饱和度对所有内容统一生效一次。
 *
 * <p><b>为什么包装 {@code draw()} 而不是 {@code render()}：</b>{@code render()} 末尾会
 * {@code draws.clear()/renderState.reset()}，只能执行一次；而 {@code draw()} 每次都会
 * 重新读取 {@code gameRenderer.mainRenderTarget()} 且不清理状态，因此可以在
 * {@code prepare()} 之后对两只眼睛各调用一次 {@code draw()}（官方 {@code executeDrawRange}
 * 用无条件指令，所以对每个目标各执行一次即可）。
 *
 * <p><b>为何要先清深度：</b>两只眼睛的离屏目标此时仍带着世界渲染留下的深度；GUI 位于
 * 正交远平面，若不先清深度会被世界几何遮挡。原版在 GUI 绘制前也会清一次主目标深度，
 * 这里对每只眼睛的目标各清一次，保持同样语义。
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

	@WrapOperation(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;draw()V")
	)
	private void rebof3d$wrapGuiDraw(GuiRenderer instance, Operation<Void> original) {
		Minecraft minecraft = Minecraft.getInstance();
		Object renderer = minecraft == null ? null : (Object) minecraft.gameRenderer;
		AnaglyphParams params = Reborn3DRenderer.params();

		if (!(renderer instanceof MainTargetAccess access) || params == null || !params.enabled || !access.rebof3d$isStereoActive()) {
			original.call(instance);
			return;
		}

		RenderTarget trueMain = access.rebof3d$getMainRenderTarget();
		try {
			for (int eye = 0; eye < 2; eye++) {
				RenderTarget eyeTarget = Reborn3DRenderer.renderer().eyeTarget(eye);
				access.rebof3d$setMainRenderTarget(eyeTarget);
				// 清该眼离屏目标的深度，使 GUI 不被世界几何遮挡（对齐原版 GUI 前清深度）。
				RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(eyeTarget.getDepthTexture(), 0.0);
				original.call(instance);
			}
		} finally {
			access.rebof3d$setMainRenderTarget(trueMain);
		}

		// GUI 已写入左右眼目标，与已合成的世界一起重新做一次 anaglyph 合成，
		// 使 GUI 也恢复为完整彩色通道。
		Reborn3DRenderer.renderer().composite(trueMain, params);
	}
}
