package dev.kestrel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Named bundles of Kestrel settings. Applying one just calls Config.set for each line. */
public final class Presets {
	private static final Map<String, String> ALL = new LinkedHashMap<>();
	private static String current = "balanced";

	static {
		//          name        entMax tntF tntC smF smC  beMax part anim fps min entB beB  expl snd tick pMax shader shadow
		ALL.put("potato",   build(48,  12,  32,  12, 28,  32,  600, 3,  45, 30, 120, 100, 1,  2,  32, 32,  50, 32));
		ALL.put("low",      build(64,  16,  40,  14, 32,  40, 1000, 2,  50, 35, 200, 160, 2,  3,  40, 48,  60, 40));
		ALL.put("balanced", build(96,  24,  56,  20, 44,  56, 1500, 1,  60, 40, 300, 250, 3,  4,  48, 64,  70, 48));
		ALL.put("quality",  build(160, 40,  96,  32, 72,  96, 5000, 1,  60, 60, 800, 600, 8, 10,  80, 96,  85, 80));
		ALL.put("off", """
				enabled=false
				adaptive_render_distance=false
				""");
	}

	private Presets() {
	}

	private static String build(int entityMax, int tntFull, int tntCut, int smallFull, int smallCut, int beMax,
								int particles, int anim, int fps, int minScale, int entityBudget, int beBudget,
								int explosionEffects, int explosionSounds, int tickDistance, int particleMax,
								int shaderScale, int shadowDistance) {
		return String.join("\n",
				"enabled=true",
				"entity_culling=true",
				"entity_max_distance=" + entityMax,
				"entity_budget=" + entityBudget,
				"tnt_lod=true",
				"tnt_full=" + tntFull,
				"tnt_cut=" + tntCut,
				"small_entity_lod=true",
				"small_full=" + smallFull,
				"small_cut=" + smallCut,
				"entity_tick_throttle=true",
				"entity_tick_distance=" + tickDistance,
				"block_entity_culling=true",
				"block_entity_max_distance=" + beMax,
				"block_entity_budget=" + beBudget,
				"particle_limit=true",
				"particle_budget=" + particles,
				"particle_max_distance=" + particleMax,
				"particle_behind_cull=true",
				"explosion_effect_cap=" + explosionEffects,
				"explosion_sound_cap=" + explosionSounds,
				"texture_animation_divisor=" + anim,
				"adaptive=true",
				"target_fps=" + fps,
				"min_scale_percent=" + minScale,
				"shader_mode=true",
				"shader_scale_percent=" + shaderScale,
				"shadow_pass_distance=" + shadowDistance);
	}

	public static List<String> names() {
		return List.copyOf(ALL.keySet());
	}

	public static String current() {
		return current;
	}

	public static boolean apply(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		String body = ALL.get(key);
		if (body == null) {
			return false;
		}
		for (String line : body.split("\n")) {
			int eq = line.indexOf('=');
			if (eq > 0) {
				Config.set(line.substring(0, eq).trim(), line.substring(eq + 1));
			}
		}
		current = key;
		return true;
	}

	public static String next() {
		List<String> names = names();
		return names.get((names.indexOf(current) + 1) % names.size());
	}
}
