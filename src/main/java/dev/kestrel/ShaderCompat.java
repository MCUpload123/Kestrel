package dev.kestrel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Talks to Iris (and forks that keep its API) by name, only when it is installed.
 * With a shader pack every entity is drawn twice (shadow pass + main pass) and the GPU is the bottleneck,
 * so Kestrel needs to know when it is in the shadow pass and when a pack is on.
 */
final class ShaderCompat {
	private static final Logger LOG = LoggerFactory.getLogger("Kestrel");
	private static final MethodHandle IN_USE;
	private static final MethodHandle SHADOW_PASS;

	static {
		MethodHandle inUse = null;
		MethodHandle shadow = null;
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodType type = MethodType.methodType(boolean.class);
			inUse = lookup.findVirtual(api, "isShaderPackInUse", type).bindTo(instance);
			shadow = lookup.findVirtual(api, "isRenderingShadowPass", type).bindTo(instance);
			LOG.info("Iris API found, shader aware mode available");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			inUse = null;
			shadow = null;
		}
		IN_USE = inUse;
		SHADOW_PASS = shadow;
	}

	private ShaderCompat() {
	}

	static boolean installed() {
		return IN_USE != null;
	}

	/** True while a shader pack is loaded and active. Cheap enough to call once a second. */
	static boolean packActive() {
		if (IN_USE == null) {
			return false;
		}
		try {
			return (boolean) IN_USE.invokeExact();
		} catch (Throwable t) {
			return false;
		}
	}

	/** True while Iris renders the shadow map. Called per entity, so it stays a bare method handle call. */
	static boolean shadowPass() {
		if (SHADOW_PASS == null) {
			return false;
		}
		try {
			return (boolean) SHADOW_PASS.invokeExact();
		} catch (Throwable t) {
			return false;
		}
	}
}
