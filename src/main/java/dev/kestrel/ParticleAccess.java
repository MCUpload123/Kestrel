package dev.kestrel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Reads the position fields of a Particle without compiling against them.
 * If a field is missing in some version the feature turns itself off.
 */
final class ParticleAccess {
	private static final Logger LOG = LoggerFactory.getLogger("Kestrel");
	private static final VarHandle X;
	private static final VarHandle Y;
	private static final VarHandle Z;

	static {
		VarHandle x = null;
		VarHandle y = null;
		VarHandle z = null;
		try {
			Class<?> type = Class.forName("net.minecraft.client.particle.Particle");
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
			x = lookup.findVarHandle(type, "x", double.class);
			y = lookup.findVarHandle(type, "y", double.class);
			z = lookup.findVarHandle(type, "z", double.class);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("particle positions not readable, distance based particle limits are off ({})", e.toString());
			x = null;
			y = null;
			z = null;
		}
		X = x;
		Y = y;
		Z = z;
	}

	private ParticleAccess() {
	}

	static boolean available() {
		return X != null && Y != null && Z != null;
	}

	static double x(Object particle) {
		return (double) X.get(particle);
	}

	static double y(Object particle) {
		return (double) Y.get(particle);
	}

	static double z(Object particle) {
		return (double) Z.get(particle);
	}
}
