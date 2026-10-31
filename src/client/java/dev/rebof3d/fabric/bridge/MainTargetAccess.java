package dev.rebof3d.fabric.bridge;

import com.mojang.blaze3d.pipeline.RenderTarget;

/**
 * 由 {@code GameRendererMixin} 在 {@code GameRenderer} 上实现的访问器。
 *
 * <p>注意：本接口<b>不能</b>放在 mixin 包（{@code dev.rebof3d.fabric.mixin}）里——Mixin 框架
 * 禁止直接引用声明为 mixin 包下的类型（会抛
 * {@code IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly}）。
 * 故独立放在 {@code bridge} 包中，供 mixin 实现与普通代码调用。
 *
 * <p>用于在 {@code GuiRendererMixin} 中切换 {@code GameRenderer} 的当前渲染目标
 * （{@code mainRenderTarget} 是私有 final 字段，只能由 {@code GameRendererMixin} 直接写），
 * 以及查询本帧是否真的执行了立体世界渲染。
 */
public interface MainTargetAccess {

	/** 切换当前渲染目标（GUI 逐眼绘制时切到该眼睛的离屏目标）。 */
	void rebof3d$setMainRenderTarget(RenderTarget target);

	/** 读取当前渲染目标（逐眼绘制结束后用于恢复真实主目标）。 */
	RenderTarget rebof3d$getMainRenderTarget();

	/** 本帧是否已按立体方式渲染过世界（决定 GUI 是否需要逐眼绘制并合成）。 */
	boolean rebof3d$isStereoActive();
}
