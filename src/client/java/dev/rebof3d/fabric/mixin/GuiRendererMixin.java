package dev.rebof3d.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.fabric.bridge.MainTargetAccess;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让界面（GUI / HUD / 屏幕效果）在 anaglyph 立体模式下正确显示颜色。
 *
 * <p><b>为什么这样修（对齐原版 1.12.2）：</b>官方 GUI 在 {@code renderLevel()} 之后单独全彩绘制一次。
 * 立体合成后成品图像的 R 通道来自右眼图、G/B 来自左眼图，GUI 本身是完整彩色不参与合成，
 * 于是戴红青眼镜时 GUI 的 R 只进右眼、G/B 只进左眼 → 通道被眼镜抢走，界面发暗偏色。
 * 原版 1.12.2 的解决办法不是逐眼渲染 GUI，而是在 anaglyph 开启时对所有纹理（含 GUI 图集）做
 * 「亮度→R、偏红→G、偏青→B」的整屏校色（见 {@code cdt.c(int)}）。本 mixin 复刻该做法：
 *
 * <ol>
 *   <li>把图形绘制目标切到一张透明底离屏目标 {@link dev.rebof3d.fabric.render.AnaglyphRenderer#guiTarget()}，
 *       并清为透明（alpha=0）与深度 0；GUI 经 {@code TRANSLUCENT} 混合会自然累积「覆盖度」alpha。</li>
 *   <li>正常绘制一次 GUI（不再逐眼渲染），完成后把主目标切回真主目标。</li>
 *   <li>调用 {@code gradeGuiAndBlend}：用与原版相同的校色公式 + 全局伽玛/饱和度处理 GUI 图层，
 *       再按预乘 alpha 混合回已合成的世界。</li>
 * </ol>
 *
 * <p>当 {@link AnaglyphParams#uiColorCorrect} 关闭时，退回原版行为：直接把 GUI 画到主目标上。
 *
 * <p><b>为何包装 {@code draw()} 而不是 {@code render()}：</b>{@code render()} 末尾会
 * {@code draws.clear()/renderState.reset()}，只能执行一次；{@code draw()} 每次都会重新读取
 * {@code gameRenderer.mainRenderTarget()} 且不清理状态，适合在此切换目标。
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
		RenderTarget guiTarget = Reborn3DRenderer.renderer().guiTarget();

		// uiColorCorrect 关闭：保持原版观感，直接画到主目标（不再做离屏校色）。
		if (!params.uiColorCorrect || guiTarget == null) {
			original.call(instance);
			return;
		}

		try {
			// 清 GUI 图层为全透明并清深度，避免上一帧残留 & 被世界几何遮挡。
			Reborn3DRenderer.renderer().clearGuiTarget(trueMain.width, trueMain.height);
			access.rebof3d$setMainRenderTarget(guiTarget);
			original.call(instance);
		} finally {
			access.rebof3d$setMainRenderTarget(trueMain);
		}

		// 对 GUI 图层做原版整屏校色，并按 alpha 混合回已合成的世界。
		Reborn3DRenderer.renderer().gradeGuiAndBlend(trueMain, params);
	}
}
