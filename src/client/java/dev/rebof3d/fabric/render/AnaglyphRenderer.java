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
import net.minecraft.client.renderer.ProjectionMatrixBuffer;

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

	/** 配置 UBO 各字段首尾相接后的原始字节数（std140：vec3 + vec3 + float + float = 36）。 */
	private static final int CONFIG_UBO_RAW_SIZE = new Std140SizeCalculator()
		.putVec3()   // LeftFilter
		.putVec3()   // RightFilter
		.putFloat()  // Gamma
		.putFloat()  // Saturation
		.get();

	/**
	 * 配置 UBO 实际分配的字节数：std140 要求结构体总大小按最大成员对齐（vec3 → 16）向上取整，
	 * 驱动侧 {@code GL_UNIFORM_BLOCK_DATA_SIZE} 实为 48。这里补齐到 16 的整数倍，尾部 12 字节
	 * 始终为 0（allocateDirect 清零且从不写入）。
	 */
	private static final int CONFIG_UBO_SIZE = (CONFIG_UBO_RAW_SIZE + 15) & ~15;

	private RenderTarget leftTarget;
	private RenderTarget rightTarget;

	private GpuBuffer configUbo;

	/**
	 * 每只眼睛各自的世界/手部投影 UBO。
	 *
	 * <p><b>关键：</b>原版 {@code ProjectionMatrixBuffer} 每个实例只有一个 GPU buffer，
	 * {@code getBuffer(...)} 会原地覆盖它。若两个眼睛共用一个实例，两遍渲染写入同一 buffer，
	 * 迟后的执行会读到彼此覆盖后的值 → 两眼画面完全相同、没有任何视差。
	 * 这里为每只眼睛各分配一个独立实例，确保两遍渲染引用不同的 buffer，视差才成立。
	 */
	private ProjectionMatrixBuffer[] levelProjectionBuffers;
	private ProjectionMatrixBuffer[] hudProjectionBuffers;

	/**
	 * CPU 侧打包缓冲区。
	 *
	 * <p><b>必须使用 {@link java.nio.ByteOrder#nativeOrder()}：</b>{@link ByteBuffer#allocateDirect(int)}
	 * 默认是 BIG_ENDIAN，而 GPU 按小端读取。若用默认字节序，{@code putFloat(1.0f)} 会写出
	 * {@code 3f 80 00 00}，GPU 读成 {@code 0x0000803f}（≈4.6e-41≈0），导致整个配置块恒为 0。
	 * 这与 vanilla {@code Std140Builder.onStack()}（MemoryStack 为原生字节序）保持一致。
	 */
	private final ByteBuffer configScratch = ByteBuffer.allocateDirect(CONFIG_UBO_SIZE).order(java.nio.ByteOrder.nativeOrder());

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

	/** 取得某只眼睛专用的世界投影 UBO（首次调用时按 "3d-reborn level eyeN" 创建）。 */
	public ProjectionMatrixBuffer levelProjectionBuffer(int eye) {
		if (levelProjectionBuffers == null) {
			levelProjectionBuffers = new ProjectionMatrixBuffer[] {
				new ProjectionMatrixBuffer("3d-reborn level eye0"),
				new ProjectionMatrixBuffer("3d-reborn level eye1")
			};
		}
		return levelProjectionBuffers[eye == 0 ? 0 : 1];
	}

	/** 取得某只眼睛专用的手部投影 UBO（首次调用时按 "3d-reborn hud eyeN" 创建）。 */
	public ProjectionMatrixBuffer hudProjectionBuffer(int eye) {
		if (hudProjectionBuffers == null) {
			hudProjectionBuffers = new ProjectionMatrixBuffer[] {
				new ProjectionMatrixBuffer("3d-reborn hud eye0"),
				new ProjectionMatrixBuffer("3d-reborn hud eye1")
			};
		}
		return hudProjectionBuffers[eye == 0 ? 0 : 1];
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
		// 字段共写入 36 字节；补足到 48（std140 尾部按 16 对齐），这 12 字节保持 0。
		configScratch.position(CONFIG_UBO_SIZE);
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
		if (levelProjectionBuffers != null) {
			for (ProjectionMatrixBuffer b : levelProjectionBuffers) {
				b.close();
			}
			levelProjectionBuffers = null;
		}
		if (hudProjectionBuffers != null) {
			for (ProjectionMatrixBuffer b : hudProjectionBuffers) {
				b.close();
			}
			hudProjectionBuffers = null;
		}
	}
}
