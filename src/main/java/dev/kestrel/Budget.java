package dev.kestrel;

import java.util.Arrays;

/**
 * Nearest-first budget. Things are counted into 2 block distance buckets during a frame;
 * when the frame ends the cutoff distance is the point where the running total passes the limit.
 * The next frame drops whatever is farther than that. Tightens quickly, relaxes slowly, so it does not flicker.
 */
final class Budget {
	private static final int BUCKETS = 128;
	private static final double BUCKET_SIZE = 2.0;
	private static final double OFF = BUCKETS * BUCKET_SIZE;

	private final int[] counts = new int[BUCKETS];
	private double cutoff = OFF;

	void add(double distance) {
		int b = (int) (distance / BUCKET_SIZE);
		counts[b >= BUCKETS ? BUCKETS - 1 : Math.max(b, 0)]++;
	}

	/** Current cutoff in blocks; OFF-sized when nothing is being dropped. */
	double cutoff() {
		return cutoff;
	}

	boolean active() {
		return cutoff < OFF;
	}

	void roll(int limit, double floor) {
		double target = OFF;
		if (limit > 0) {
			int sum = 0;
			for (int i = 0; i < BUCKETS; i++) {
				sum += counts[i];
				if (sum > limit) {
					target = Math.max(floor, i * BUCKET_SIZE);
					break;
				}
			}
		}
		Arrays.fill(counts, 0);

		if (limit <= 0) {
			cutoff = OFF;
		} else if (target < cutoff) {
			cutoff = cutoff >= OFF ? target : cutoff * 0.75 + target * 0.25;
		} else if (target > cutoff) {
			cutoff = Math.min(target, cutoff + 1.0);
		}
	}
}
