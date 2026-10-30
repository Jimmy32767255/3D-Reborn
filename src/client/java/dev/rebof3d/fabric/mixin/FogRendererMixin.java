package dev.rebof3d.fabric.mixin;

import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.core.StereoMath;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 立体模式下修正雾色/清屏色，对应原版 1.12.2 {@code EntityRenderer}（{@code buq.java:1724-1733}）。
 *
 * <p>原版在开启 anaglyph 后、把雾色交给 {@code glClearColor} 之前插了一段颜色重算：
 * 雾色被替换为「亮度(30/59/11) + 偏红(30/70) + 偏青(30/70)」的红青对称色。原因是立体模式下
 * 左眼只能看到红通道、右眼只能看到青通道，未修正的雾色（例如明亮的蓝天）会让两只眼看到的
 * 底色亮度严重失衡。这是原版唯一一处针对立体模式的整屏颜色校正。
 *
 * <p>在 26.3 中，该颜色由 {@code FogRenderer.setupFog(...)} 产出到 {@code FogData.color}，
 * 随后同时用于清屏（{@code LevelRenderer.render} 的 clearPass）与雾效 UBO，因此在这里修正覆盖即可。
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

	@Unique
	private final float[] rebof3d$fogScratch = new float[3];

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void rebof3d$correctAnaglyphFogColor(CallbackInfoReturnable<FogData> cir) {
		AnaglyphParams params = Reborn3DRenderer.params();
		if (params == null || !params.enabled) {
			return;
		}

		FogData fog = cir.getReturnValue();
		if (fog == null || fog.color == null) {
			return;
		}

		Vector4f color = fog.color;
		StereoMath.correctFogColor(color.x, color.y, color.z, this.rebof3d$fogScratch);
		color.x = this.rebof3d$fogScratch[0];
		color.y = this.rebof3d$fogScratch[1];
		color.z = this.rebof3d$fogScratch[2];
	}
}
