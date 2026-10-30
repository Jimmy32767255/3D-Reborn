package dev.rebof3d.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 参数的 JSON 持久化。使用 Gson（Fabric 与 NeoForge 两个加载器都会在 classpath 上提供）。
 * 不依赖任何 MC 类型，便于跨加载器复用。
 *
 * <p>文件格式示例：
 * <pre>
 * {
 *   "enabled": true,
 *   "cameraSpacing": 1.0,
 *   "leftFilterR": 0.0, "leftFilterG": 1.0, "leftFilterB": 1.0,
 *   "rightFilterR": 1.0, "rightFilterG": 0.0, "rightFilterB": 0.0,
 *   "gamma": 1.0, "saturation": 1.0
 * }
 * </pre>
 */
public final class AnaglyphConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private AnaglyphParams params = new AnaglyphParams();

	public AnaglyphParams getParams() {
		return params;
	}

	public void setParams(AnaglyphParams params) {
		this.params = params != null ? params : new AnaglyphParams();
	}

	/** 从文件读取；文件不存在或解析失败时返回默认配置（不抛出，避免 mod 拖垮启动）。 */
	public static AnaglyphConfig load(Path path) {
		AnaglyphConfig cfg = new AnaglyphConfig();
		if (path == null || !Files.isRegularFile(path)) {
			return cfg;
		}
		try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			AnaglyphConfig loaded = GSON.fromJson(r, AnaglyphConfig.class);
			if (loaded != null && loaded.params != null) {
				return loaded;
			}
		} catch (Exception e) {
			// 静默回退到默认，交由调用方记录日志
			AnaglyphConfig fallback = new AnaglyphConfig();
			fallback.loadError = e;
			return fallback;
		}
		return cfg;
	}

	/** 写回文件（自动创建父目录）。 */
	public void save(Path path) throws IOException {
		if (path == null) {
			return;
		}
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(this, w);
		}
	}

	// 仅用于把 load 阶段的异常带给调用方记录日志（不序列化）。
	transient Throwable loadError;

	/** 若上一次 {@link #load(Path)} 发生异常，返回该异常；否则返回 {@code null}。 */
	public Throwable getLoadError() {
		return loadError;
	}
}
