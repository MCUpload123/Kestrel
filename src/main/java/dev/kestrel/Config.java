package dev.kestrel;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Every public boolean/int/String field is a setting. The key in the config file
 * is the field name in snake_case (tntFull -> tnt_full). Adding a setting is adding a field.
 */
public final class Config {
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface Doc {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface Range {
		int min();

		int max();

		int step() default 1;
	}

	private static final Logger LOG = LoggerFactory.getLogger("Kestrel");
	private static final Config INSTANCE = new Config();
	private static final Config DEFAULTS = new Config();
	private static final List<Field> FIELDS = collect();
	private static volatile int version;

	public static Config get() {
		return INSTANCE;
	}

	/** Bumped on every change, lets caches notice that settings moved. */
	public static int version() {
		return version;
	}

	// ---- master ------------------------------------------------------------------------

	@Doc("Master switch for every culling / LOD / throttle module. Handy for A/B tests: turn this off in the Presets tab.")
	public boolean enabled = true;

	// ---- entities ----------------------------------------------------------------------

	@Doc("Skip ordinary entities (mobs, armor stands, ...) farther than entity_max_distance. Players are never skipped.")
	public boolean entityCulling = true;
	@Range(min = 16, max = 512, step = 8)
	@Doc("Ordinary entities farther than this (in blocks) are not drawn.")
	public int entityMaxDistance = 96;
	@Range(min = 0, max = 5000, step = 25)
	@Doc("Entities drawn per frame (counted in front of the camera), nearest first. Far ones are dropped when over. 0 = off.")
	public int entityBudget = 300;
	@Doc("Per mob distance, e.g. zombie:32,armorstand:24,itemframe:24 (matches part of the class name).")
	public String entityOverrides = "";
	@Doc("Names that are never culled, e.g. villager,irongolem.")
	public String entityNeverCull = "";

	@Doc("Primed TNT: full detail inside tnt_full, every second one up to tnt_cut, nothing beyond.")
	public boolean tntLod = true;
	@Range(min = 8, max = 256, step = 4)
	@Doc("TNT inside this distance is drawn in full.")
	public int tntFull = 24;
	@Range(min = 16, max = 512, step = 8)
	@Doc("TNT farther than this is not drawn; between the two distances every second one is drawn.")
	public int tntCut = 56;

	@Doc("Dropped items, xp orbs and falling blocks, same banding as TNT.")
	public boolean smallEntityLod = true;
	@Range(min = 8, max = 256, step = 4)
	@Doc("Items, xp orbs and falling blocks inside this distance are drawn in full.")
	public int smallFull = 20;
	@Range(min = 16, max = 512, step = 8)
	@Doc("Items, xp orbs and falling blocks farther than this are not drawn.")
	public int smallCut = 44;

	@Doc("Tick far TNT, items, xp orbs and falling blocks only every entity_tick_divisor ticks (client side, saves physics).")
	public boolean entityTickThrottle = true;
	@Range(min = 16, max = 256, step = 8)
	@Doc("Beyond this distance the throttle applies.")
	public int entityTickDistance = 48;
	@Range(min = 2, max = 8)
	@Doc("Far entities tick once every N ticks.")
	public int entityTickDivisor = 4;

	// ---- block entities ------------------------------------------------------------------

	@Doc("Skip chests, signs, banners, ... farther than block_entity_max_distance. Beacons and portals are exempt.")
	public boolean blockEntityCulling = true;
	@Range(min = 16, max = 512, step = 8)
	@Doc("Block entities farther than this (in blocks) are not drawn.")
	public int blockEntityMaxDistance = 56;
	@Range(min = 0, max = 5000, step = 25)
	@Doc("Block entities drawn per frame, nearest first. 0 = off.")
	public int blockEntityBudget = 250;

	// ---- particles, explosions, sound ----------------------------------------------------

	@Doc("Particle limiter: nearest first budget, distance limit, and no particles behind the camera.")
	public boolean particleLimit = true;
	@Range(min = 100, max = 100000, step = 100)
	@Doc("Particles drawn per frame, nearest first.")
	public int particleBudget = 1500;
	@Range(min = 16, max = 256, step = 8)
	@Doc("Particles farther than this (in blocks) are not drawn.")
	public int particleMaxDistance = 64;
	@Doc("Never draw particles behind you (first person only).")
	public boolean particleBehindCull = true;

	@Range(min = 0, max = 64)
	@Doc("Explosion particle effects handled per client tick, the rest are skipped. 0 = off.")
	public int explosionEffectCap = 3;
	@Range(min = 0, max = 64)
	@Doc("Explosion sounds played per client tick, the rest are skipped. 0 = off.")
	public int explosionSoundCap = 4;

	@Range(min = 1, max = 8)
	@Doc("Advance animated textures (water, lava, fire) only every Nth call. 1 = vanilla.")
	public int textureAnimationDivisor = 1;

	// ---- governor --------------------------------------------------------------------------

	@Doc("Shrinks every distance and budget (down to min_scale_percent) while FPS stays under target_fps.")
	public boolean adaptive = true;
	@Range(min = 20, max = 360, step = 5)
	@Doc("The governor shrinks distances and budgets while FPS is below this.")
	public int targetFps = 60;
	@Range(min = 20, max = 100, step = 5)
	@Doc("How far the governor may shrink everything, in percent.")
	public int minScalePercent = 40;

	@Doc("Lowers the render distance option when FPS is low even at the minimum scale. Rewrites options.txt.")
	public boolean adaptiveRenderDistance = false;
	@Range(min = 2, max = 32)
	@Doc("Lowest render distance (chunks) the adaptive render distance may reach.")
	public int minRenderDistance = 6;

	// ---- shader packs (Iris) -------------------------------------------------------------------

	@Doc("When a shader pack is active the GPU is the bottleneck: shrink every distance and budget by shader_scale_percent.")
	public boolean shaderMode = true;
	@Range(min = 30, max = 100, step = 5)
	@Doc("How much of the normal distances and budgets are kept while a shader pack is active.")
	public int shaderScalePercent = 70;
	@Range(min = 16, max = 256, step = 8)
	@Doc("Shader packs draw entities a second time into the shadow map. Entities farther than this are left out of that pass.")
	public int shadowPassDistance = 48;

	// ---- resource use -----------------------------------------------------------------------

	@Range(min = 0, max = 240, step = 5)
	@Doc("FPS limit while a menu is open in a world (inventory, pause). Chat is not limited. 0 = off.")
	public int menuFpsCap = 30;
	@Range(min = 0, max = 240, step = 5)
	@Doc("FPS limit while the window is not focused. 0 = off.")
	public int unfocusedFpsCap = 10;

	@Doc("Adjust thread priorities: render thread up, integrated server and worker threads down. Mostly a Windows effect.")
	public boolean threadPriorities = true;
	@Range(min = 1, max = 10)
	@Doc("Java priority of the render thread (5 is normal).")
	public int renderThreadPriority = 6;
	@Range(min = 1, max = 10)
	@Doc("Java priority of the integrated server thread.")
	public int serverThreadPriority = 4;
	@Range(min = 1, max = 10)
	@Doc("Java priority of chunk and background workers.")
	public int workerThreadPriority = 3;

	@Doc("Ask the JVM to collect garbage when you leave a world.")
	public boolean gcOnDisconnect = true;

	@Doc("On the first world join, pick potato/low automatically when the PC looks weak (few cores or little heap). Backup is kept.")
	public boolean autoTune = true;
	@Doc("Set by Kestrel after the auto tune check ran once.")
	public boolean autoTuneDone = false;

	// ---- display ----------------------------------------------------------------------------

	@Doc("Small stats box in the top right corner.")
	public boolean hud = true;
	@Doc("Extra HUD line: heap use and current cutoff distances.")
	public boolean hudExtended = false;
	@Doc("One log line per minute with what was skipped.")
	public boolean statsLog = true;
	@Doc("Show the Kestrel button on the title, pause and options screens.")
	public boolean uiButton = true;
	@Doc("Set by Kestrel after the welcome message was shown once.")
	public boolean introShown = false;

	// ---- reflection helpers -------------------------------------------------------------------

	private static List<Field> collect() {
		List<Field> out = new ArrayList<>();
		for (Field f : Config.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			Class<?> t = f.getType();
			if (t == boolean.class || t == int.class || t == String.class) {
				out.add(f);
			}
		}
		return out;
	}

	private static String keyOf(Field f) {
		return f.getName().replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
	}

	private static Field find(String key) {
		String wanted = key.toLowerCase(Locale.ROOT);
		for (Field f : FIELDS) {
			if (keyOf(f).equals(wanted)) {
				return f;
			}
		}
		return null;
	}

	public static List<String> keys() {
		List<String> out = new ArrayList<>();
		for (Field f : FIELDS) {
			out.add(keyOf(f));
		}
		return out;
	}

	/** @return the current value as text, or null for an unknown key */
	public static String get(String key) {
		Field f = find(key);
		if (f == null) {
			return null;
		}
		try {
			return String.valueOf(f.get(INSTANCE));
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	/** @return null on success, otherwise a message for the player */
	public static String set(String key, String raw) {
		Field f = find(key);
		if (f == null) {
			return "unknown setting '" + key + "'";
		}
		String text = raw.trim();
		try {
			if (f.getType() == boolean.class) {
				switch (text.toLowerCase(Locale.ROOT)) {
					case "true", "on", "yes", "1" -> f.setBoolean(INSTANCE, true);
					case "false", "off", "no", "0" -> f.setBoolean(INSTANCE, false);
					default -> {
						return keyOf(f) + " is on/off";
					}
				}
			} else if (f.getType() == int.class) {
				int v = Integer.parseInt(text);
				Range r = f.getAnnotation(Range.class);
				if (r != null) {
					v = Math.max(r.min(), Math.min(r.max(), v));
				}
				f.setInt(INSTANCE, v);
			} else {
				f.set(INSTANCE, text.equalsIgnoreCase("none") ? "" : text);
			}
			version++;
			return null;
		} catch (NumberFormatException e) {
			return keyOf(f) + " needs a whole number";
		} catch (IllegalAccessException e) {
			return "cannot change " + keyOf(f);
		}
	}

	// ---- helpers for the settings screen --------------------------------------------------------

	public static boolean isBool(String key) {
		Field f = find(key);
		return f != null && f.getType() == boolean.class;
	}

	public static boolean getBool(String key) {
		return "true".equals(get(key));
	}

	public static int getInt(String key) {
		try {
			return Integer.parseInt(String.valueOf(get(key)));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static String defaultOf(String key) {
		Field f = find(key);
		try {
			return f == null ? null : String.valueOf(f.get(DEFAULTS));
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	public static String doc(String key) {
		Field f = find(key);
		Doc d = f == null ? null : f.getAnnotation(Doc.class);
		return d == null ? "" : d.value();
	}

	public static int min(String key) {
		Field f = find(key);
		Range r = f == null ? null : f.getAnnotation(Range.class);
		return r == null ? Integer.MIN_VALUE : r.min();
	}

	public static int max(String key) {
		Field f = find(key);
		Range r = f == null ? null : f.getAnnotation(Range.class);
		return r == null ? Integer.MAX_VALUE : r.max();
	}

	public static int step(String key) {
		Field f = find(key);
		Range r = f == null ? null : f.getAnnotation(Range.class);
		return r == null ? 1 : r.step();
	}

	/** "entity_max_distance" -> "Entity max distance" */
	public static String label(String key) {
		String text = key.replace('_', ' ');
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	// ---- file ---------------------------------------------------------------------------------

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kestrel.properties");
	}

	public static void load() {
		Path file = file();
		boolean existed = Files.exists(file);
		if (existed) {
			Properties p = new Properties();
			try (BufferedReader in = Files.newBufferedReader(file)) {
				p.load(in);
			} catch (IOException | IllegalArgumentException e) {
				LOG.warn("could not read {}, keeping current values", file, e);
				return;
			}
			for (Field f : FIELDS) {
				String v = p.getProperty(keyOf(f));
				if (v != null) {
					String err = set(keyOf(f), v);
					if (err != null) {
						LOG.warn("{}: {}", file.getFileName(), err);
					}
				}
			}
		}
		version++;
		// rewrite so keys added by newer versions show up in the file
		save();
	}

	public static void save() {
		StringBuilder sb = new StringBuilder("# Kestrel settings. Most settings can be changed from the in-game Kestrel screen.\n# Distances are in blocks.\n");
		for (Field f : FIELDS) {
			Doc doc = f.getAnnotation(Doc.class);
			sb.append('\n');
			if (doc != null) {
				sb.append("# ").append(doc.value()).append('\n');
			}
			sb.append(keyOf(f)).append('=').append(get(keyOf(f))).append('\n');
		}
		try (BufferedWriter out = Files.newBufferedWriter(file())) {
			out.write(sb.toString());
		} catch (IOException e) {
			LOG.warn("could not write {}", file(), e);
		}
	}
}
