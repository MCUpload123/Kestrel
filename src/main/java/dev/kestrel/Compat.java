package dev.kestrel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Things that moved between 26.x releases (the open screen, setScreen) or that are cheap to look up
 * by name, so a rename cannot break compilation.
 */
final class Compat {
	private static MethodHandle windowActive;
	private static Function<Minecraft, Object> screenGetter;

	static {
		try {
			windowActive = MethodHandles.lookup()
					.findVirtual(Minecraft.class, "isWindowActive", MethodType.methodType(boolean.class));
		} catch (ReflectiveOperationException | RuntimeException e) {
			windowActive = null;
		}
	}

	private Compat() {
	}

	static boolean windowActive(Minecraft mc) {
		if (windowActive == null) {
			return true;
		}
		try {
			return (boolean) windowActive.invokeExact(mc);
		} catch (Throwable t) {
			windowActive = null;
			return true;
		}
	}

	// ---- current screen ---------------------------------------------------------------------

	/** The screen that is open right now, or null. */
	static Object screen(Minecraft mc) {
		if (screenGetter == null) {
			screenGetter = resolveScreenGetter();
		}
		try {
			return screenGetter.apply(mc);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static Function<Minecraft, Object> resolveScreenGetter() {
		Function<Object, Object> direct = member(Minecraft.class);
		if (direct != null) {
			return direct::apply;
		}
		try {
			Field gui = Minecraft.class.getField("gui");
			Function<Object, Object> inner = member(gui.getType());
			if (inner != null) {
				return mc -> inner.apply(read(gui, mc));
			}
		} catch (NoSuchFieldException ignored) {
			// fall through
		}
		return mc -> null;
	}

	private static Function<Object, Object> member(Class<?> type) {
		try {
			Field f = type.getField("screen");
			return o -> read(f, o);
		} catch (NoSuchFieldException ignored) {
			// try methods
		}
		for (String name : new String[] {"screen", "getScreen"}) {
			try {
				Method m = type.getMethod(name);
				return o -> call(m, o);
			} catch (NoSuchMethodException ignored) {
				// next name
			}
		}
		return null;
	}

	private static Object read(Field f, Object target) {
		try {
			return f.get(target);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private static Object call(Method m, Object target) {
		try {
			return m.invoke(target);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	// ---- opening a screen -------------------------------------------------------------------

	/** Opens a screen (null closes). 26.2 renamed Minecraft.setScreen, so every known spelling is tried. */
	static void show(Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		if (invokeSetter(mc, "setScreenAndShow", screen) || invokeSetter(mc, "setScreen", screen)) {
			return;
		}
		try {
			Object gui = Minecraft.class.getField("gui").get(mc);
			invokeSetter(gui, "setScreen", screen);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// nothing else to try
		}
	}

	private static boolean invokeSetter(Object target, String name, Screen screen) {
		for (Method m : target.getClass().getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == 1
					&& m.getParameterTypes()[0].isAssignableFrom(Screen.class)) {
				try {
					m.invoke(target, screen);
					return true;
				} catch (ReflectiveOperationException | RuntimeException e) {
					return false;
				}
			}
		}
		return false;
	}

	/** Screen#addRenderableWidget is protected, and Fabric's Screens helper changed names, so call it directly. */
	static boolean addWidget(Screen screen, Object widget) {
		for (Method m : Screen.class.getDeclaredMethods()) {
			if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1) {
				try {
					m.setAccessible(true);
					m.invoke(screen, widget);
					return true;
				} catch (ReflectiveOperationException | RuntimeException e) {
					return false;
				}
			}
		}
		return false;
	}
}
