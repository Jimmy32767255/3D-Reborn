package dev.rebof3d.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.core.StereoMath;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截 {@code GameRenderer.renderLevel()}，实现 anaglyph 的双遍渲染：
 *
 * <ol>
 *   <li>{@code @Redirect} 捕获 {@code projectionMatrix}（含 bob/传送门扭曲后的最终投影）；</li>
 *   <li>{@code @WrapOperation} 拦截 {@code levelRenderer.render(...)}：三维开启时渲染两次，
 *       每次偏移投影并切换到对应眼睛的离屏目标；再恢复主目标并执行合成；关闭时直通。</li>
 * </ol>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	@Final
	@Mutable
	private RenderTarget mainRenderTarget;

	@Shadow
	@Final
	private ProjectionMatrixBuffer levelProjectionMatrixBuffer;

	@Unique
	private final Matrix4f rebof3d$capturedProjection = new Matrix4f();

	/** 捕获最终投影矩阵（renderLevel 中 line 661 的 getBuffer(Matrix4f) 调用）。 */
	@Redirect(
		method = "renderLevel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;")
	)
	private GpuBufferSlice rebof3d$captureProjection(ProjectionMatrixBuffer instance, Matrix4f projectionMatrix) {
		this.rebof3d$capturedProjection.set(projectionMatrix);
		return instance.getBuffer(projectionMatrix);
	}

	/** 拦截世界渲染调用，做双遍 + 合成。 */
	@WrapOperation(
		method = "renderLevel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V")
	)
	private void rebof3d$wrapLevelRender(
		LevelRenderer levelRenderer,
		com.mojang.blaze3d.resource.GraphicsResourceAllocator resourceAllocator,
		boolean renderOutline,
		CameraRenderState cameraState,
		GpuBufferSlice terrainFog,
		Vector4f fogColor,
		boolean shouldRenderSky,
		boolean consistentDepthRequired,
		Operation<Void> original
	) {
		AnaglyphParams params = Reborn3DRenderer.params();
		RenderTarget trueMain = this.mainRenderTarget;

		if (params == null || !params.enabled) {
			original.call(levelRenderer, resourceAllocator, renderOutline, cameraState, terrainFog, fogColor, shouldRenderSky, consistentDepthRequired);
			return;
		}

		try {
			Reborn3DRenderer.renderer().ensureTargets(trueMain.width, trueMain.height);

			for (int eye = 0; eye < 2; eye++) {
				// 偏移投影
				Matrix4f shifted = new Matrix4f(this.rebof3d$capturedProjection);
				shifted.translate(StereoMath.projectionShift(eye, params.cameraSpacing), 0.0f, 0.0f);
				RenderSystem.setProjectionMatrix(this.levelProjectionMatrixBuffer.getBuffer(shifted), ProjectionType.PERSPECTIVE);

				// 切换到该眼睛离屏目标
				this.mainRenderTarget = Reborn3DRenderer.renderer().eyeTarget(eye);
				original.call(levelRenderer, resourceAllocator, renderOutline, cameraState, terrainFog, fogColor, shouldRenderSky, consistentDepthRequired);
			}
		} finally {
			// 恢复真实主目标与投影
			this.mainRenderTarget = trueMain;
			RenderSystem.setProjectionMatrix(this.levelProjectionMatrixBuffer.getBuffer(this.rebof3d$capturedProjection), ProjectionType.PERSPECTIVE);
		}

		// 合成到真实主目标
		Reborn3DRenderer.renderer().composite(trueMain, params);
	}
}
