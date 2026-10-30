package dev.rebof3d.fabric.render;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 定义用于 anaglyph 合成的自定义 {@link RenderPipeline}。
 *
 * <p>该管线在 {@code dev.rebof3d.fabric.mixin.RenderPipelinesMixin} 中被追加到
 * {@code RenderPipelines.requiredPipelines()}，从而被 {@code ShaderManager} 自动编译。
 *
 * <p>采用 {@code POST_PROCESSING_SNIPPET}（含 GLOBALS 绑定组，提供 ortho 投影与全局 uniform），
 * 全屏三角形绘制，两个颜色采样器 {@code Sampler0}/{@code Sampler1} + 一个配置 UBO {@code Reborn3DConfig}。
 */
public final class AnaglyphPipeline {

	/** 管线在资源中的标识（着色器路径基于 rebof3d 命名空间）。 */
	public static final Identifier LOCATION = id("pipeline/anaglyph");

	private static final BindGroupLayout BIND_GROUP = BindGroupLayout.builder()
		.withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("Reborn3DConfig", UniformType.UNIFORM_BUFFER)
		.build();

	public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
		.withLocation(LOCATION)
		.withVertexShader(id("core/anaglyph_screenquad"))
		.withFragmentShader(id("post/anaglyph"))
		.withBindGroupLayout(BIND_GROUP)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("rebof3d", path);
	}

	private AnaglyphPipeline() {
	}
}
