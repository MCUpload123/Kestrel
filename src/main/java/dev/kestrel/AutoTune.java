package dev.kestrel;

import net.minecraft.client.Minecraft;

/** Runs once, on the first world join: weak hardware gets the potato or low profile. */
final class AutoTune {
	private static boolean ran;

	private AutoTune() {
	}

	static void tick() {
		Config c = Config.get();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (!c.introShown) {
			c.introShown = true;
			Config.save();
			Lod.say("settings: press K, or use the Kestrel button on the pause / options screen");
		}
		if (ran || !c.autoTune || c.autoTuneDone) {
			return;
		}
		ran = true;
		c.autoTuneDone = true;

		int cores = Runtime.getRuntime().availableProcessors();
		long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
		String tier = null;
		if (cores <= 2 || heapMb < 1800) {
			tier = "potato";
		} else if (cores <= 4 || heapMb < 3000) {
			tier = "low";
		}

		if (tier == null) {
			Config.save();
			return;
		}

		Presets.apply(tier);
		String video = Tuner.apply(tier);
		Config.save();

		Lod.say("weak PC detected (" + cores + " cores, " + heapMb + " MB heap): profile '" + tier + "' applied, " + video);
		Lod.say("undo: Kestrel settings > Presets > Restore video options, then Preset: balanced");
		if (heapMb < 1800) {
			Lod.say("tip: give the game 2-3 GB of RAM in the launcher (-Xmx2G or -Xmx3G)");
		}
	}
}
