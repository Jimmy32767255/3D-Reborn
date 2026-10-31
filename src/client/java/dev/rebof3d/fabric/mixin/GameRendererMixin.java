package dev.rebof3d.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.core.StereoMath;
import dev.rebof3d.fabric.bridge.MainTargetAccess;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截 GameRenderer.render() 中对整帧 renderLevel() 的调用，实现 anaglyph 双遍渲染。
 *
 * <p>相较「只包住 LevelRenderer.render」的做法，这里包住整个 renderLevel()，
 * 因此每只眼睛都会完整走一遍世界 + 手上物品/屏幕效果（render3dHud），
 * 手部从而获得与场景一致的立体视差（与原版 1.12.2 每眼各画一次手一致）。
 *
 * <p>世界渲染逐眼画进离屏目标后，先合成一次世界；随后 GUI 由 GuiRendererMixin
 * 逐眼画进同一对离屏目标并再合成一次（GUI 也恢复完整彩色通道）。
 *
 * <p>投影偏移通过两个 @Redirect 完成：
 * <ul>
 *   <li>renderLevel 里的 ProjectionMatrixBuffer.getBuffer(Matrix4f)（世界投影）；</li>
 *   <li>render3dHud 里的 ProjectionMatrixBuffer.getBuffer(Projection)（手部投影）。</li>
 * </ul>
 * 二者都按当前眼睛索引 rebof3d$currentEye 加水平平移，使每遍渲染读到该眼对应的投影。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements MainTargetAccess {

	@Shadow
	@Final
	@Mutable
	private RenderTarget mainRenderTarget;

	/** 当前正在渲染的眼睛：-1 表示非立体直通、0 左眼、1 右眼。 */
	@Unique
	private int rebof3d$currentEye = -1;

	/** 本帧是否已按立体方式渲染过世界（供 GuiRendererMixin 判断是否需要逐眼绘制 GUI）。 */
	@Unique
	private boolean rebof3d$stereoActive = false;

	/** 每帧进入 render() 时先复位标志，避免在未调用 renderLevel() 的帧（标题/加载界面）里残留上一帧的 true。 */
	@Inject(method = "render", at = @At("HEAD"))
	private void rebof3d$resetStereoFlag(CallbackInfo ci) {
		this.rebof3d$stereoActive = false;
	}

	/** 拦截 render() 中的 renderLevel() 调用，开启时渲染两遍（每眼一次）再合成世界。 */
	@WrapOperation(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V")
	)
	private void rebof3d$wrapRenderLevel(GameRenderer instance, Operation<Void> original) {
		AnaglyphParams params = Reborn3DRenderer.params();
		RenderTarget trueMain = this.mainRenderTarget;

		if (params == null || !params.enabled) {
			this.rebof3d$currentEye = -1;
			this.rebof3d$stereoActive = false;
			original.call(instance);
			return;
		}

		this.rebof3d$stereoActive = true;
		try {
			Reborn3DRenderer.renderer().ensureTargets(trueMain.width, trueMain.height);

			for (int eye = 0; eye < 2; eye++) {
				this.rebof3d$currentEye = eye;
				// 切换到该眼睛离屏目标（render3dHud 也会写入此目标，故手部同样分眼）
				this.mainRenderTarget = Reborn3DRenderer.renderer().eyeTarget(eye);
				original.call(instance);
			}
		} finally {
			this.rebof3d$currentEye = -1;
			this.mainRenderTarget = trueMain;
		}

		// 先把世界合成到真实主目标；GUI 稍后由 GuiRendererMixin 逐眼画入并再次合成。
		Reborn3DRenderer.renderer().composite(trueMain, params);
	}

	/** 世界投影：按当前眼睛用该眼专属 buffer 加水平平移（renderLevel line 661）。 */
	@Redirect(
		method = "renderLevel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;")
	)
	private GpuBufferSlice rebof3d$redirectLevelProjection(ProjectionMatrixBuffer buffer, Matrix4f projectionMatrix) {
		AnaglyphParams params = Reborn3DRenderer.params();
		if (params == null || !params.enabled || this.rebof3d$currentEye < 0) {
			return buffer.getBuffer(projectionMatrix);
		}
		Matrix4f shifted = new Matrix4f(projectionMatrix);
		shifted.translate(StereoMath.projectionShift(this.rebof3d$currentEye, params.cameraSpacing), 0.0f, 0.0f);
		// 用该眼专属 buffer，避免两眼共用同一 GPU buffer 导致画面被覆盖而无视差。
		return Reborn3DRenderer.renderer().levelProjectionBuffer(this.rebof3d$currentEye).getBuffer(shifted);
	}

	/** 手部投影：按当前眼睛用该眼专属 buffer 加相同水平平移（render3dHud line 683）。 */
	@Redirect(
		method = "render3dHud",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;")
	)
	private GpuBufferSlice rebof3d$redirectHudProjection(ProjectionMatrixBuffer buffer, Projection projection) {
		AnaglyphParams params = Reborn3DRenderer.params();
		if (params == null || !params.enabled || this.rebof3d$currentEye < 0) {
			return buffer.getBuffer(projection);
		}
		Matrix4f shifted = projection.getMatrix(new Matrix4f());
		shifted.translate(StereoMath.projectionShift(this.rebof3d$currentEye, params.cameraSpacing), 0.0f, 0.0f);
		return Reborn3DRenderer.renderer().hudProjectionBuffer(this.rebof3d$currentEye).getBuffer(shifted);
	}

	@Override
	public void rebof3d$setMainRenderTarget(RenderTarget target) {
		this.mainRenderTarget = target;
	}

	@Override
	public RenderTarget rebof3d$getMainRenderTarget() {
		return this.mainRenderTarget;
	}

	@Override
	public boolean rebof3d$isStereoActive() {
		return this.rebof3d$stereoActive;
	}
}
