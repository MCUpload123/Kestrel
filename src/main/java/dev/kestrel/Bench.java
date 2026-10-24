package dev.kestrel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The settings screen bench action records frame times and prints avg / 1% low / worst. */
final class Bench {
	private static long[] samples = new long[4096];
	private static int count;
	private static long endAt;
	private static boolean running;
	private static int seconds;
	private static final List<String> HISTORY = new ArrayList<>();

	private Bench() {
	}

	/** Latest results first, at most five. */
	static List<String> history() {
		return List.copyOf(HISTORY);
	}

	static boolean running() {
		return running;
	}

	static void start(int secs) {
		seconds = secs;
		count = 0;
		endAt = System.nanoTime() + secs * 1_000_000_000L;
		running = true;
	}

	/** Called once per uncapped frame with the frame time in nanoseconds. */
	static void sample(long dt, long now) {
		if (!running) {
			return;
		}
		if (count == samples.length) {
			samples = Arrays.copyOf(samples, count * 2);
		}
		samples[count++] = dt;
		if (now >= endAt) {
			finish();
		}
	}

	private static void finish() {
		running = false;
		if (count < 10) {
			Lod.say("bench: not enough frames");
			return;
		}
		long[] sorted = Arrays.copyOf(samples, count);
		Arrays.sort(sorted);
		long total = 0;
		for (int i = 0; i < count; i++) {
			total += sorted[i];
		}
		int worstCount = Math.max(1, count / 100);
		long worstSum = 0;
		for (int i = count - worstCount; i < count; i++) {
			worstSum += sorted[i];
		}
		double avgFps = count / (total / 1.0e9);
		double lowFps = 1.0e9 / (worstSum / (double) worstCount);
		double worstMs = sorted[count - 1] / 1.0e6;

		String result = String.format(Locale.ROOT,
				"bench %ds [%s, kestrel %s]: avg %.1f fps | 1%% low %.1f | worst %.0f ms | %d frames",
				seconds, Presets.current(), Config.get().enabled ? "on" : "off", avgFps, lowFps, worstMs, count);
		HISTORY.add(0, result);
		while (HISTORY.size() > 5) {
			HISTORY.remove(HISTORY.size() - 1);
		}
		Lod.say(result);
	}
}
