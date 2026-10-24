package dev.kestrel;

/**
 * On a weak CPU the integrated server and the chunk workers compete with the render thread.
 * Lowering theirs (and nudging the render thread up) trades a little TPS / chunk speed for FPS.
 * Java priorities are honoured on Windows, mostly ignored on Linux.
 */
final class Threads {
	private Threads() {
	}

	static void apply(Config c) {
		if (!c.threadPriorities) {
			return;
		}
		set(Thread.currentThread(), c.renderThreadPriority);

		ThreadGroup root = Thread.currentThread().getThreadGroup();
		while (root.getParent() != null) {
			root = root.getParent();
		}
		Thread[] all = new Thread[root.activeCount() + 16];
		int n = root.enumerate(all, true);
		for (int i = 0; i < n; i++) {
			Thread t = all[i];
			String name = t.getName();
			if (name.startsWith("Server thread")) {
				set(t, c.serverThreadPriority);
			} else if (name.startsWith("Worker-")) {
				set(t, c.workerThreadPriority);
			}
		}
	}

	static String describe() {
		StringBuilder sb = new StringBuilder();
		ThreadGroup root = Thread.currentThread().getThreadGroup();
		while (root.getParent() != null) {
			root = root.getParent();
		}
		Thread[] all = new Thread[root.activeCount() + 16];
		int n = root.enumerate(all, true);
		int workers = 0;
		int workerPriority = -1;
		for (int i = 0; i < n; i++) {
			String name = all[i].getName();
			if (name.startsWith("Server thread")) {
				sb.append("server ").append(all[i].getPriority()).append(", ");
			} else if (name.startsWith("Worker-")) {
				workers++;
				workerPriority = all[i].getPriority();
			}
		}
		sb.append("render ").append(Thread.currentThread().getPriority());
		if (workers > 0) {
			sb.append(", ").append(workers).append(" workers ").append(workerPriority);
		}
		return sb.toString();
	}

	private static void set(Thread t, int priority) {
		int p = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority));
		if (t.getPriority() != p) {
			try {
				t.setPriority(p);
			} catch (SecurityException ignored) {
				// not allowed here, nothing to do
			}
		}
	}
}
