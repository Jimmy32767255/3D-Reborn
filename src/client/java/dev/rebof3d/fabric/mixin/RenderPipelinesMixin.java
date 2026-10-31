package dev.rebof3d.fabric.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.rebof3d.fabric.render.AnaglyphPipeline;
import dev.rebof3d.fabric.render.GuiGradePipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 把自定义 anaglyph 合成管线注入 {@code RenderPipelines.requiredPipelines()} 的返回值，
 * 使 {@code ShaderManager.reload()} 在资源重载时用它来编译我们的着色器。
 */
@Mixin(RenderPipelines.class)
public abstract class RenderPipelinesMixin {

	@Inject(method = "requiredPipelines", at = @At("RETURN"), cancellable = true)
	private static void rebof3d$addAnaglyphPipeline(CallbackInfoReturnable<List<RenderPipeline>> cir) {
		List<RenderPipeline> original = cir.getReturnValue();
		List<RenderPipeline> augmented = new ArrayList<>(original.size() + 2);
		augmented.addAll(original);
		augmented.add(AnaglyphPipeline.PIPELINE);
		augmented.add(GuiGradePipeline.PIPELINE);
		cir.setReturnValue(augmented);
	}
}
