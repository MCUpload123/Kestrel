package dev.kestrel;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Reads and writes single entries of the entity_overrides setting ("zombie:32,armorstand:24"). */
final class Overrides {
	private Overrides() {
	}

	private static Map<String, Integer> parse() {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (String part : Config.get().entityOverrides.split(",")) {
			int colon = part.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			try {
				map.put(part.substring(0, colon).trim().toLowerCase(Locale.ROOT),
						Integer.parseInt(part.substring(colon + 1).trim()));
			} catch (NumberFormatException ignored) {
				// skip the broken entry
			}
		}
		return map;
	}

	/** Distance for this mob, or 0 when it follows the global distance. */
	static int get(String name) {
		return parse().getOrDefault(name.toLowerCase(Locale.ROOT), 0);
	}

	static void set(String name, int distance) {
		Map<String, Integer> map = parse();
		String key = name.toLowerCase(Locale.ROOT);
		if (distance <= 0) {
			map.remove(key);
		} else {
			map.put(key, Math.min(512, distance));
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> e : map.entrySet()) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(e.getKey()).append(':').append(e.getValue());
		}
		Config.set("entity_overrides", sb.length() == 0 ? "none" : sb.toString());
	}
}
