package dev.kestrel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Applies low-end friendly values to the vanilla video options.
 * Options are reached by name through reflection, so an option that was renamed or removed
 * in some version is skipped instead of breaking the build. The first run keeps a backup
 * of the original values so the Restore video options action can undo it.
 */
public final class Tuner {
	private static final Logger LOG = LoggerFactory.getLogger("Kestrel");

	private static final Map<String, Map<String, String>> PRESETS = new LinkedHashMap<>();

	static {
		Map<String, String> potato = new LinkedHashMap<>();
		potato.put("renderDistance", "6");
		potato.put("simulationDistance", "5");
		potato.put("biomeBlendRadius", "0");
		potato.put("entityDistanceScaling", "0.5");
		potato.put("entityShadows", "false");
		potato.put("particles", "MINIMAL");
		potato.put("cloudStatus", "OFF");
		potato.put("ambientOcclusion", "false");
		potato.put("mipmapLevels", "0");
		potato.put("graphicsMode", "FAST");
		potato.put("cutoutLeaves", "false");
		potato.put("improvedTransparency", "false");
		potato.put("menuBackgroundBlurriness", "0");
		potato.put("chunkSectionFadeInTime", "0.0");
		potato.put("weatherRadius", "3");
		PRESETS.put("potato", potato);

		Map<String, String> low = new LinkedHashMap<>();
		low.put("renderDistance", "8");
		low.put("simulationDistance", "6");
		low.put("biomeBlendRadius", "1");
		low.put("entityDistanceScaling", "0.75");
		low.put("entityShadows", "false");
		low.put("particles", "DECREASED");
		low.put("cloudStatus", "FAST");
		low.put("ambientOcclusion", "false");
		low.put("mipmapLevels", "2");
		low.put("graphicsMode", "FAST");
		low.put("improvedTransparency", "false");
		low.put("menuBackgroundBlurriness", "0");
		low.put("chunkSectionFadeInTime", "0.0");
		PRESETS.put("low", low);
	}

	private Tuner() {
	}

	public static List<String> names() {
		List<String> out = new ArrayList<>(PRESETS.keySet());
		out.add("restore");
		return out;
	}

	private static Path backupFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kestrel-options-backup.properties");
	}

	// ---- public entry points ----------------------------------------------------------

	public static String apply(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		if (key.equals("restore")) {
			return restore();
		}
		Map<String, String> preset = PRESETS.get(key);
		if (preset == null) {
			return "unknown option preset '" + name + "'";
		}

		Properties backup = loadBackup();
		int applied = 0;
		List<String> skipped = new ArrayList<>();

		for (Map.Entry<String, String> e : preset.entrySet()) {
			Object option = option(e.getKey());
			if (option == null) {
				skipped.add(e.getKey());
				continue;
			}
			Object current = read(option);
			Object target = convert(current, e.getValue());
			if (current == null || target == null) {
				skipped.add(e.getKey());
				continue;
			}
			backup.putIfAbsent(e.getKey(), text(current));
			if (write(option, target)) {
				applied++;
			} else {
				skipped.add(e.getKey());
			}
		}

		saveBackup(backup);
		saveOptions();
		String msg = "applied " + applied + " video options (" + key + ")";
		if (!skipped.isEmpty()) {
			msg += ", skipped: " + String.join(", ", skipped);
		}
		return msg;
	}

	public static String restore() {
		Path file = backupFile();
		if (!Files.exists(file)) {
			return "nothing to restore";
		}
		Properties backup = loadBackup();
		int restored = 0;
		for (String name : backup.stringPropertyNames()) {
			Object option = option(name);
			if (option == null) {
				continue;
			}
			Object target = convert(read(option), backup.getProperty(name));
			if (target != null && write(option, target)) {
				restored++;
			}
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			LOG.warn("could not delete {}", file, e);
		}
		saveOptions();
		return "restored " + restored + " video options";
	}

	/** Current value of an integer option such as renderDistance, or -1 when unavailable. */
	public static int getInt(String name) {
		Object v = read(option(name));
		return v instanceof Integer i ? i : -1;
	}

	public static void setInt(String name, int value) {
		Object option = option(name);
		if (option != null && read(option) instanceof Integer) {
			write(option, value);
		}
	}

	// ---- reflection plumbing ------------------------------------------------------------

	private static Object option(String name) {
		try {
			Object options = Minecraft.getInstance().options;
			Method m = options.getClass().getMethod(name);
			return m.invoke(options);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object read(Object option) {
		if (option == null) {
			return null;
		}
		try {
			return option.getClass().getMethod("get").invoke(option);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static boolean write(Object option, Object value) {
		try {
			option.getClass().getMethod("set", Object.class).invoke(option, value);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static void saveOptions() {
		try {
			Object options = Minecraft.getInstance().options;
			options.getClass().getMethod("save").invoke(options);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("could not save options.txt", e);
		}
	}

	private static String text(Object v) {
		return v instanceof Enum<?> e ? e.name() : String.valueOf(v);
	}

	/** Turns a text token into a value of the same type as the option currently holds. */
	private static Object convert(Object current, String token) {
		if (current == null) {
			return null;
		}
		String t = token.trim();
		try {
			if (current instanceof Boolean) {
				return switch (t.toLowerCase(Locale.ROOT)) {
					case "off", "false", "fast", "no", "0" -> Boolean.FALSE;
					default -> Boolean.TRUE;
				};
			}
			if (current instanceof Integer) {
				return Integer.valueOf(t);
			}
			if (current instanceof Double) {
				return Double.valueOf(t);
			}
			if (current instanceof Enum<?> e) {
				Object[] constants = e.getDeclaringClass().getEnumConstants();
				for (Object k : constants) {
					if (((Enum<?>) k).name().equalsIgnoreCase(t)) {
						return k;
					}
				}
				if (t.equalsIgnoreCase("false")) {
					for (Object k : constants) {
						if (((Enum<?>) k).name().equalsIgnoreCase("OFF")) {
							return k;
						}
					}
				}
			}
		} catch (NumberFormatException ignored) {
			// falls through to null
		}
		return null;
	}

	private static Properties loadBackup() {
		Properties p = new Properties();
		Path file = backupFile();
		if (Files.exists(file)) {
			try (BufferedReader in = Files.newBufferedReader(file)) {
				p.load(in);
			} catch (IOException | IllegalArgumentException e) {
				LOG.warn("could not read {}", file, e);
			}
		}
		return p;
	}

	private static void saveBackup(Properties p) {
		if (p.isEmpty()) {
			return;
		}
		try (BufferedWriter out = Files.newBufferedWriter(backupFile())) {
			p.store(out, "Original video options before Kestrel changed them. Use Restore video options in the settings screen.");
		} catch (IOException e) {
			LOG.warn("could not write backup", e);
		}
	}
}
