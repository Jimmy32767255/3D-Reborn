package dev.rebof3d.fabric.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.rebof3d.core.AnaglyphParams;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 运行时渲染器：持有左右眼离屏渲染目标与配置 UBO，负责最终 anaglyph 合成。
 *
 * <p>主渲染目标的切换由 {@code GameRendererMixin}（持有 accessor）负责，本类只做离屏目标的
 * 分配/尺寸维护与合成 pass 的执行，职责单一，便于测试与移植。
 */
public final class AnaglyphRenderer {

	/** 配置 UBO 的字节大小（std140：vec3 + vec3 + float + float）。 */
	private static final int CONFIG_UBO_SIZE = new Std140SizeCalculator()
		.putVec3()   // LeftFilter
		.putVec3()   // RightFilter
		.putFloat()  // Gamma
		.putFloat()  // Saturation
		.get();

	private RenderTarget leftTarget;
	private RenderTarget rightTarget;

	private GpuBuffer configUbo;
	private final ByteBuffer configScratch = ByteBuffer.allocateDirect(CONFIG_UBO_SIZE);

	/** 按给定尺寸确保离屏目标存在，尺寸变化时重建。 */
	public void ensureTargets(int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		if (leftTarget == null) {
			leftTarget = new MainTarget(width, height);
			rightTarget = new MainTarget(width, height);
		} else if (leftTarget.width != width || leftTarget.height != height) {
			leftTarget.resize(width, height);
			rightTarget.resize(width, height);
		}
	}

	public RenderTarget eyeTarget(int eye) {
		return eye == 0 ? leftTarget : rightTarget;
	}

	/**
	 * 把左右眼纹理合成到给定主目标。
	 */
	public void composite(RenderTarget mainTarget, AnaglyphParams params) {
		ensureTargets(mainTarget.width, mainTarget.height);
		updateConfigUbo(params);

		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder encoder = device.createCommandEncoder();
		CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipeline(AnaglyphPipeline.PIPELINE);
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		try (RenderPass pass = encoder.createRenderPass(
			() -> "3D-Reborn anaglyph composite",
			mainTarget.getColorTextureView(),
			Optional.empty(),
			null,
			OptionalDouble.empty()
		)) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("Reborn3DConfig", configUbo);
			pass.setUniform("Sampler0", leftTarget.getColorTextureView(), sampler);
			pass.setUniform("Sampler1", rightTarget.getColorTextureView(), sampler);
			pass.draw(3, 1, 0, 0);
		}
	}

	/** 把参数打包进持久 UBO（首次创建，之后回写）。 */
	private void updateConfigUbo(AnaglyphParams p) {
		configScratch.clear();
		Std140Builder builder = Std140Builder.intoBuffer(configScratch);
		builder.putVec3(p.leftFilterR, p.leftFilterG, p.leftFilterB);
		builder.putVec3(p.rightFilterR, p.rightFilterG, p.rightFilterB);
		builder.putFloat(p.gamma);
		builder.putFloat(p.saturation);
		configScratch.flip();

		GpuDevice device = RenderSystem.getDevice();
		if (configUbo == null) {
			// 必须同时带 USAGE_COPY_DST：后续回写走 writeToBuffer（GPU 拷贝），
			// 仅 USAGE_UNIFORM 会在第二帧回写时抛
			// "Buffer needs USAGE_COPY_DST to be a destination for a copy"。
			configUbo = device.createBuffer(
				() -> "3D-Reborn config UBO",
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
				configScratch);
		} else {
			device.createCommandEncoder().writeToBuffer(configUbo.slice(0L, CONFIG_UBO_SIZE), configScratch);
		}
	}

	public void close() {
		if (leftTarget != null) {
			leftTarget.destroyBuffers();
			leftTarget = null;
		}
		if (rightTarget != null) {
			rightTarget.destroyBuffers();
			rightTarget = null;
		}
		if (configUbo != null) {
			configUbo.close();
			configUbo = null;
		}
	}
}
