package dev.rebof3d.fabric.gui;

import dev.rebof3d.core.AnaglyphParams;
import dev.rebof3d.fabric.config.ClientConfig;
import dev.rebof3d.fabric.render.Reborn3DRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Anaglyph 3D 的参数设置界面：开关、摄像机间距、左右滤光片颜色、伽玛校准、饱和度。
 * 所有改动实时写入运行时参数（{@link Reborn3DRenderer}），点击「完成」时落盘。
 */
public final class AnaglyphConfigScreen extends Screen {

	private static final int WIDGET_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW = 24;
	private static final int TOP = 40;

	private final Screen parent;
	private final AnaglyphParams params;

	public AnaglyphConfigScreen(Screen parent) {
		super(Component.translatable("rebof3d.screen.title"));
		this.parent = parent;
		this.params = Reborn3DRenderer.config().getParams();
	}

	@Override
	protected void init() {
		int cx = this.width / 2 - WIDGET_WIDTH / 2;
		int y = TOP;

		// 3D 开关
		Button enableBtn = Button.builder(enabledLabel(), b -> {
			params.enabled = !params.enabled;
			b.setMessage(enabledLabel());
		}).bounds(cx, y, WIDGET_WIDTH, WIDGET_HEIGHT).build();
		this.addRenderableWidget(enableBtn);
		y += ROW;

		// 摄像机间距 0.0 .. 3.0
		addSlider(cx, y, "rebof3d.opt.camera_spacing", 0.0, 3.0, 100,
			() -> params.cameraSpacing, v -> params.cameraSpacing = (float) v,
			v -> String.format(Locale.ROOT, "%.2f", v));
		y += ROW;

		// 伽玛 0.5 .. 2.5
		addSlider(cx, y, "rebof3d.opt.gamma", 0.5, 2.5, 100,
			() -> params.gamma, v -> params.gamma = (float) v,
			v -> String.format(Locale.ROOT, "%.2f", v));
		y += ROW;

		// 饱和度 0.0 .. 2.0
		addSlider(cx, y, "rebof3d.opt.saturation", 0.0, 2.0, 100,
			() -> params.saturation, v -> params.saturation = (float) v,
			v -> String.format(Locale.ROOT, "%.2f", v));
		y += ROW;

		// 左眼滤光片 RGB
		addSlider(cx, y, "rebof3d.opt.left_r", 0.0, 1.0, 100,
			() -> params.leftFilterR, v -> params.leftFilterR = (float) v, AnaglyphConfigScreen::fmt01);
		y += ROW;
		addSlider(cx, y, "rebof3d.opt.left_g", 0.0, 1.0, 100,
			() -> params.leftFilterG, v -> params.leftFilterG = (float) v, AnaglyphConfigScreen::fmt01);
		y += ROW;
		addSlider(cx, y, "rebof3d.opt.left_b", 0.0, 1.0, 100,
			() -> params.leftFilterB, v -> params.leftFilterB = (float) v, AnaglyphConfigScreen::fmt01);
		y += ROW;

		// 右眼滤光片 RGB
		addSlider(cx, y, "rebof3d.opt.right_r", 0.0, 1.0, 100,
			() -> params.rightFilterR, v -> params.rightFilterR = (float) v, AnaglyphConfigScreen::fmt01);
		y += ROW;
		addSlider(cx, y, "rebof3d.opt.right_g", 0.0, 1.0, 100,
			() -> params.rightFilterG, v -> params.rightFilterG = (float) v, AnaglyphConfigScreen::fmt01);
		y += ROW;
		addSlider(cx, y, "rebof3d.opt.right_b", 0.0, 1.0, 100,
			() -> params.rightFilterB, v -> params.rightFilterB = (float) v, AnaglyphConfigScreen::fmt01);
		y += ROW;

		// 完成
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
			.bounds(cx, y + 8, WIDGET_WIDTH, WIDGET_HEIGHT).build());
	}

	private void addSlider(int x, int y, String key, double min, double max, int steps,
						   DoubleSupplier getter, DoubleConsumer setter,
						   java.util.function.DoubleFunction<String> fmt) {
		double normalized = (clamp(getter.getAsDouble(), min, max) - min) / (max - min);
		this.addRenderableWidget(new ParamSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
			Component.translatable(key), normalized, min, max, setter, fmt));
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static String fmt01(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private Component enabledLabel() {
		return Component.translatable(params.enabled ? "rebof3d.opt.enabled.on" : "rebof3d.opt.enabled.off");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		ClientConfig.save();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** 通用参数滑块：内部 value 归一化 0..1，applyValue 时映射到 [min,max] 并回调。 */
	private static final class ParamSlider extends AbstractSliderButton {
		private final double min;
		private final double max;
		private final DoubleConsumer setter;
		private final java.util.function.DoubleFunction<String> fmt;
		private final Component baseLabel;

		ParamSlider(int x, int y, int w, int h, Component label, double normalized,
					double min, double max, DoubleConsumer setter,
					java.util.function.DoubleFunction<String> fmt) {
			super(x, y, w, h, label, normalized);
			this.baseLabel = label;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.fmt = fmt;
			this.applyValue();
			this.updateMessage();
		}

		private double actual() {
			return this.min + this.value * (this.max - this.min);
		}

		@Override
		protected void updateMessage() {
			this.setMessage(this.baseLabel.copy().append(": " + this.fmt.apply(actual())));
		}

		@Override
		protected void applyValue() {
			this.setter.accept(actual());
		}
	}
}
