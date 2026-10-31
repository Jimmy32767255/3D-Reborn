package dev.rebof3d.fabric.render;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 把「单独渲染的 GUI 图层」校色后按 alpha 混合回已合成世界的自定义管线。
 *
 * <p>与 {@link AnaglyphPipeline} 相似，均为全屏三角形后处理，但：
 * <ul>
 *   <li>只采样一张纹理 {@code Sampler0}（GUI 图层，透明底、预乘 alpha）；</li>
 *   <li>复用同一 {@code Reborn3DConfig} UBO（只用到 Gamma / Saturation）；</li>
 *   <li>颜色目标使用 {@link BlendFunction#TRANSLUCENT_PREMULTIPLIED_ALPHA}，把校色后的
 *       GUI 与已有世界像素混合（源=预乘 alpha 的 GUI，目标=世界）。</li>
 * </ul>
 *
 * <p>该管线在 {@code RenderPipelinesMixin} 中与 anaglyph 管线一起追加，
 * 以便 {@code ShaderManager} 在资源重载时编译。
 */
public final class GuiGradePipeline {

	public static final Identifier LOCATION = id("pipeline/gui_grade");

	private static final BindGroupLayout BIND_GROUP = BindGroupLayout.builder()
		.withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("Reborn3DConfig", UniformType.UNIFORM_BUFFER)
		.build();

	public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
		.withLocation(LOCATION)
		.withVertexShader(id("core/anaglyph_screenquad"))
		.withFragmentShader(id("post/gui_grade"))
		.withBindGroupLayout(BIND_GROUP)
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
		.build();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("rebof3d", path);
	}

	private GuiGradePipeline() {
	}
}
