package dev.kestrel;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;

/**
 * All decisions live here so the mixins stay one-liners. Everything runs on the render thread
 * except onClientTick, which runs on the client thread (the same thread in practice).
 */
public final class Lod {
	private static final Logger LOG = LoggerFactory.getLogger("Kestrel");

	/** Nothing closer than this is ever culled. */
	private static final double SAFE_RADIUS = 12.0;
	private static final double SAFE_PARTICLE_RADIUS = 6.0;

	public static final int HOOK_FRAME = 0;
	public static final int HOOK_ENTITY = 1;
	public static final int HOOK_PARTICLE = 2;
	public static final int HOOK_BLOCK_ENTITY = 3;
	public static final int HOOK_TEXTURE = 4;
	public static final int HOOK_ENTITY_TICK = 5;
	public static final int HOOK_SOUND = 6;
	public static final int HOOK_EXPLOSION = 7;
	private static final String[] HOOK_NAMES = {
			"frame timer", "entity culling", "particle limiter", "block entity culling",
			"texture animation throttle", "entity tick throttle", "explosion sound cap", "explosion effect cap"};
	private static final boolean[] hooked = new boolean[HOOK_NAMES.length];

	private static final int C_ENTITY = 0;
	private static final int C_PARTICLE = 1;
	private static final int C_BLOCK = 2;
	private static final int C_ANIM = 3;
	private static final int C_TICK = 4;
	private static final int C_SOUND = 5;
	private static final int C_EXPLOSION = 6;
	private static final int COUNTERS = 7;
	private static final long[] window = new long[COUNTERS];
	private static final long[] shown = new long[COUNTERS];
	private static final long[] minute = new long[COUNTERS];
	private static int framesInWindow;
	private static int framesShown = 1;

	// governor and frame timing
	private static float scale = 1.0f;
	private static boolean shadersOn;
	private static float shaderFactor = 1.0f;
	private static double emaNanos;
	private static long lastFrame;
	private static long nextSecond;
	private static long nextEval;
	private static long nextStats;
	private static long nextRenderDistance;
	private static long nextThreads;
	private static boolean runTickSeen;
	private static final long[] frameTimes = new long[1200];
	private static int frameIndex;
	private static int frameFill;
	private static double lowFps;

	// per frame camera data
	private static Entity camEntity;
	private static boolean lookValid;
	private static double eyeX;
	private static double eyeY;
	private static double eyeZ;
	private static double lookX;
	private static double lookY;
	private static double lookZ;
	private static boolean camSampled;
	private static boolean thirdPerson;
	private static double camX;
	private static double camY;
	private static double camZ;

	private static final Budget entityBudget = new Budget();
	private static final Budget blockBudget = new Budget();
	private static final Budget particleBudget = new Budget();
	private static int particlesThisFrame;

	// per tick state
	private static int tickPhase;
	private static int explosionEffects;
	private static int explosionSounds;
	private static int animCalls;

	private static int baseRenderDistance = -1;
	private static int lastSetRenderDistance = -1;

	// caches that depend on the config text
	private static int cacheVersion = -1;
	private static final Map<Class<?>, Integer> overrideCache = new HashMap<>();
	private static final Map<Class<?>, Boolean> neverCullCache = new HashMap<>();
	private static final Map<Object, Boolean> explodeSounds = new IdentityHashMap<>();

	private static final Pattern EXEMPT_BLOCK_ENTITY = Pattern.compile("(?i)beacon|gateway|portal|conduit");
	private static final ClassValue<Boolean> EXEMPT_BLOCK_ENTITY_TYPE = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			return EXEMPT_BLOCK_ENTITY.matcher(type.getSimpleName()).find();
		}
	};

	private Lod() {
	}

	/** Governor scale combined with the shader pack factor; this is what distances and budgets use. */
	private static float effScale() {
		return scale * shaderFactor;
	}

	private static void refreshShaders(Config c) {
		shadersOn = c.shaderMode && ShaderCompat.packActive();
		shaderFactor = shadersOn ? c.shaderScalePercent / 100f : 1.0f;
	}

	static void announce() {
		LOG.info("Kestrel loaded, preset {}, master {}", Presets.current(), Config.get().enabled ? "on" : "off");
	}

	/** Logs once the first time a hook actually runs, so latest.log shows which ones are live. */
	private static void hook(int id) {
		if (!hooked[id]) {
			hooked[id] = true;
			LOG.info("hook active: {}", HOOK_NAMES[id]);
		}
	}

	private static void count(int which) {
		window[which]++;
		minute[which]++;
	}

	static void say(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[Kestrel] " + message));
		} else {
			LOG.info(message);
		}
	}

	// ---- frame timer, FPS caps, governor ----------------------------------------------------

	/** Called by the runTick mixin. */
	public static void onRunTick() {
		runTickSeen = true;
		frame();
	}

	/** Called from the HUD layer; only drives the timer when the mixin never fired. */
	public static void onHudFrame() {
		if (!runTickSeen) {
			frame();
		}
	}

	/** Called once per client tick. */
	public static void onClientTick() {
		tickPhase++;
		explosionEffects = 0;
		explosionSounds = 0;
	}

	private static int fpsCap(Minecraft mc, Config c) {
		if (mc.level == null) {
			return 0;
		}
		if (c.unfocusedFpsCap > 0 && !Compat.windowActive(mc)) {
			return c.unfocusedFpsCap;
		}
		if (c.menuFpsCap > 0) {
			Object screen = Compat.screen(mc);
			if (screen != null) {
				String name = screen.getClass().getSimpleName();
				if (!name.contains("Chat") && !name.contains("Kestrel")) {
					return c.menuFpsCap;
				}
			}
		}
		return 0;
	}

	private static void frame() {
		hook(HOOK_FRAME);
		Minecraft mc = Minecraft.getInstance();
		Config c = Config.get();
		long now = System.nanoTime();

		int cap = fpsCap(mc, c);
		if (cap > 0 && lastFrame != 0) {
			long wait = lastFrame + 1_000_000_000L / cap - now;
			if (wait > 200_000L) {
				LockSupport.parkNanos(wait);
				now = System.nanoTime();
			}
		}

		long dt = lastFrame != 0 ? now - lastFrame : 0;
		lastFrame = now;
		// capped frames and world-load hitches would drag the average and fool the governor
		if (cap == 0 && dt > 0 && dt < 250_000_000L) {
			emaNanos = emaNanos == 0 ? dt : emaNanos + (dt - emaNanos) * 0.05;
			frameTimes[frameIndex] = dt;
			frameIndex = (frameIndex + 1) % frameTimes.length;
			frameFill = Math.min(frameFill + 1, frameTimes.length);
			Bench.sample(dt, now);
		}

		particlesThisFrame = 0;
		framesInWindow++;
		camSampled = false;
		updateCamera(mc);

		if (cache(c)) {
			// bucket the budgets with last frame's counts, then they filter this frame
			double scaleFloor = Math.max(shadersOn ? 0.25f : 0.35f, effScale());
			entityBudget.roll((int) (c.entityBudget * scaleFloor), SAFE_RADIUS);
			blockBudget.roll((int) (c.blockEntityBudget * scaleFloor), SAFE_RADIUS);
			particleBudget.roll((int) (c.particleBudget * scaleFloor), SAFE_PARTICLE_RADIUS);
		}

		double fps = fps();

		if (now >= nextSecond) {
			nextSecond = now + 1_000_000_000L;
			System.arraycopy(window, 0, shown, 0, COUNTERS);
			Arrays.fill(window, 0);
			framesShown = Math.max(1, framesInWindow);
			framesInWindow = 0;
			lowFps = computeLow();
			refreshShaders(c);
		}

		if (now >= nextEval) {
			nextEval = now + 500_000_000L;
			if (c.adaptive && c.enabled && fps > 0) {
				float min = c.minScalePercent / 100f;
				if (fps < c.targetFps * 0.9) {
					scale = Math.max(min, scale - 0.08f);
				} else if (fps > c.targetFps * 1.15) {
					scale = Math.min(1.0f, scale + 0.04f);
				}
			} else if (!c.adaptive || !c.enabled) {
				scale = 1.0f;
			}
		}

		if (now >= nextThreads) {
			nextThreads = now + 10_000_000_000L;
			Threads.apply(c);
			AutoTune.tick();
		}

		if (c.adaptiveRenderDistance && c.enabled && fps > 0 && now >= nextRenderDistance) {
			nextRenderDistance = now + 8_000_000_000L;
			adjustRenderDistance(c, fps);
		}

		if (c.statsLog) {
			if (nextStats == 0) {
				nextStats = now + 60_000_000_000L;
			} else if (now >= nextStats) {
				nextStats = now + 60_000_000_000L;
				long total = 0;
				for (long v : minute) {
					total += v;
				}
				if (total > 0) {
					LOG.info("last minute: {} entities, {} particles, {} block entities, {} texture ticks, {} entity ticks, {} sounds, {} explosion effects skipped; scale {}, ~{} fps",
							minute[C_ENTITY], minute[C_PARTICLE], minute[C_BLOCK], minute[C_ANIM], minute[C_TICK],
							minute[C_SOUND], minute[C_EXPLOSION], Math.round(scale * 100) / 100.0, Math.round(fps));
					Arrays.fill(minute, 0);
				}
			}
		}
	}

	private static void updateCamera(Minecraft mc) {
		Entity cam = mc.getCameraEntity();
		camEntity = cam;
		lookValid = cam != null;
		if (cam == null) {
			return;
		}
		eyeX = cam.getX();
		eyeY = cam.getEyeY();
		eyeZ = cam.getZ();
		double yaw = Math.toRadians(cam.getYRot());
		double pitch = Math.toRadians(cam.getXRot());
		double cp = Math.cos(pitch);
		lookX = -Math.sin(yaw) * cp;
		lookY = -Math.sin(pitch);
		lookZ = Math.cos(yaw) * cp;
	}

	/** The renderer tells us where the real camera is the first time it asks about an entity. */
	private static void sampleCamera(double x, double y, double z) {
		if (camSampled) {
			return;
		}
		camSampled = true;
		camX = x;
		camY = y;
		camZ = z;
		double ex = x - eyeX;
		double ey = y - eyeY;
		double ez = z - eyeZ;
		// a camera 2.5+ blocks from the eyes means third person, where the look vector is not the view
		thirdPerson = !lookValid || ex * ex + ey * ey + ez * ez > 6.25;
	}

	private static boolean inFront(double dx, double dy, double dz) {
		return thirdPerson || dx * lookX + dy * lookY + dz * lookZ > 0;
	}

	private static double computeLow() {
		if (frameFill < 30) {
			return 0;
		}
		long[] copy = Arrays.copyOf(frameTimes, frameFill);
		Arrays.sort(copy);
		int worst = Math.max(1, frameFill / 100);
		long sum = 0;
		for (int i = frameFill - worst; i < frameFill; i++) {
			sum += copy[i];
		}
		return 1.0e9 / (sum / (double) worst);
	}

	private static void adjustRenderDistance(Config c, double fps) {
		if (Minecraft.getInstance().level == null) {
			return;
		}
		int current = Tuner.getInt("renderDistance");
		if (current < 0) {
			return;
		}

		// the player changed it by hand: that is the new ceiling
		if (baseRenderDistance < 0 || (lastSetRenderDistance != -1 && current != lastSetRenderDistance)) {
			baseRenderDistance = current;
			lastSetRenderDistance = -1;
		}

		boolean atFloor = scale <= c.minScalePercent / 100f + 0.01f;
		if (fps < c.targetFps * 0.8 && atFloor && current > c.minRenderDistance) {
			Tuner.setInt("renderDistance", current - 1);
			lastSetRenderDistance = current - 1;
		} else if (fps > c.targetFps * 1.5 && current < baseRenderDistance) {
			Tuner.setInt("renderDistance", current + 1);
			lastSetRenderDistance = current + 1;
		}
	}

	public static void onDisconnect() {
		scale = 1.0f;
		baseRenderDistance = -1;
		lastSetRenderDistance = -1;
		explodeSounds.clear();
		if (Config.get().gcOnDisconnect) {
			Thread t = new Thread(System::gc, "kestrel-gc");
			t.setDaemon(true);
			t.start();
		}
	}

	public static double fps() {
		return emaNanos > 0 ? 1.0e9 / emaNanos : 0;
	}

	public static int scalePercent() {
		return Math.round(effScale() * 100);
	}

	/** Drops the per-class caches when the settings text changed. */
	private static boolean cache(Config c) {
		if (cacheVersion != Config.version()) {
			cacheVersion = Config.version();
			overrideCache.clear();
			neverCullCache.clear();
		}
		return c.enabled;
	}

	// ---- entities ---------------------------------------------------------------------------

	/** @return true when the entity should be skipped this frame */
	public static boolean cullEntity(Entity e, double cx, double cy, double cz) {
		hook(HOOK_ENTITY);
		Config c = Config.get();
		if (!c.enabled || e instanceof Player) {
			return false;
		}
		if (ShaderCompat.shadowPass()) {
			return cullShadowEntity(e, c);
		}
		sampleCamera(cx, cy, cz);

		double dx = e.getX() - cx;
		double dy = e.getY() - cy;
		double dz = e.getZ() - cz;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (c.entityBudget > 0 && inFront(dx, dy, dz)) {
			entityBudget.add(dist);
		}
		if (dist <= SAFE_RADIUS || e == camEntity) {
			return false;
		}

		Class<?> type = e.getClass();
		if (neverCull(type)) {
			return false;
		}

		boolean tnt = e instanceof PrimedTnt;
		boolean small = e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof FallingBlockEntity;
		// big things (dragons, ghasts, boats) stay visible from far away
		boolean big = e.getBbWidth() >= 3.0f || e.getBbHeight() >= 3.0f;

		boolean cull = false;
		if (tnt) {
			cull = c.tntLod && band(e.getId(), dist, c.tntFull, c.tntCut);
		} else if (small) {
			cull = c.smallEntityLod && band(e.getId(), dist, c.smallFull, c.smallCut);
		} else if (c.entityCulling && !big) {
			int max = overrideFor(type);
			if (max < 0) {
				max = c.entityMaxDistance;
			}
			cull = dist > Math.max(SAFE_RADIUS, max * (0.5 + 0.5 * effScale()));
		}

		if (!cull && !big && c.entityBudget > 0 && dist > entityBudget.cutoff()) {
			cull = true;
		}

		if (cull) {
			count(C_ENTITY);
		}
		return cull;
	}

	/** Three zones: full detail, half of them (picked by entity id so it never flickers), none. */
	private static boolean band(int id, double dist, int full, int cut) {
		double s = effScale();
		double near = Math.max(SAFE_RADIUS, full * s);
		double far = Math.max(near + 8.0, cut * s);
		if (dist <= near) {
			return false;
		}
		if (dist <= far) {
			return (id & 1) != 0;
		}
		return true;
	}

	/**
	 * Shadow pass of a shader pack: the camera data of the main view does not apply (things behind you
	 * still cast shadows into view), so only the distance from the player counts.
	 */
	private static boolean cullShadowEntity(Entity e, Config c) {
		if (!lookValid || e == camEntity) {
			return false;
		}
		if (e.getBbWidth() >= 3.0f || e.getBbHeight() >= 3.0f) {
			return false;
		}
		double dx = e.getX() - eyeX;
		double dy = e.getY() - eyeY;
		double dz = e.getZ() - eyeZ;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		boolean cull = dist > Math.max(SAFE_RADIUS * 2, c.shadowPassDistance * (0.5 + 0.5 * effScale()));
		if (cull) {
			count(C_ENTITY);
		}
		return cull;
	}

	private static int overrideFor(Class<?> type) {
		String text = Config.get().entityOverrides;
		if (text.isEmpty()) {
			return -1;
		}
		Integer cached = overrideCache.get(type);
		if (cached == null) {
			cached = -1;
			String name = type.getSimpleName().toLowerCase(Locale.ROOT);
			for (String part : text.split(",")) {
				int colon = part.indexOf(':');
				if (colon <= 0) {
					continue;
				}
				String key = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
				if (!key.isEmpty() && name.contains(key)) {
					try {
						cached = Integer.parseInt(part.substring(colon + 1).trim());
					} catch (NumberFormatException ignored) {
						// bad entry, skip it
					}
					break;
				}
			}
			overrideCache.put(type, cached);
		}
		return cached;
	}

	private static boolean neverCull(Class<?> type) {
		String text = Config.get().entityNeverCull;
		if (text.isEmpty()) {
			return false;
		}
		Boolean cached = neverCullCache.get(type);
		if (cached == null) {
			cached = false;
			String name = type.getSimpleName().toLowerCase(Locale.ROOT);
			for (String part : text.split(",")) {
				String key = part.trim().toLowerCase(Locale.ROOT);
				if (!key.isEmpty() && name.contains(key)) {
					cached = true;
					break;
				}
			}
			neverCullCache.put(type, cached);
		}
		return cached;
	}

	// ---- entity ticking ---------------------------------------------------------------------

	/** Far TNT / items / xp / falling blocks are invisible anyway, so most of their client ticks are skipped. */
	public static boolean skipEntityTick(Entity e) {
		hook(HOOK_ENTITY_TICK);
		Config c = Config.get();
		if (!c.enabled || !c.entityTickThrottle) {
			return false;
		}
		if (!(e instanceof PrimedTnt || e instanceof ItemEntity || e instanceof FallingBlockEntity || e instanceof ExperienceOrb)) {
			return false;
		}
		Entity cam = camEntity;
		if (cam == null) {
			return false;
		}
		double dx = e.getX() - cam.getX();
		double dy = e.getY() - cam.getY();
		double dz = e.getZ() - cam.getZ();
		double limit = Math.max(SAFE_RADIUS * 2, c.entityTickDistance);
		if (dx * dx + dy * dy + dz * dz <= limit * limit) {
			return false;
		}
		if (Math.floorMod(e.getId() + tickPhase, c.entityTickDivisor) == 0) {
			return false;
		}
		count(C_TICK);
		return true;
	}

	// ---- block entities ---------------------------------------------------------------------

	public static boolean cullBlockEntity(BlockEntity be) {
		hook(HOOK_BLOCK_ENTITY);
		Config c = Config.get();
		if (!c.enabled || !c.blockEntityCulling || (!lookValid && !camSampled)) {
			return false;
		}

		if (ShaderCompat.shadowPass()) {
			return cullShadowBlockEntity(be, c);
		}

		BlockPos pos = be.getBlockPos();
		double dx = pos.getX() + 0.5 - (camSampled ? camX : eyeX);
		double dy = pos.getY() + 0.5 - (camSampled ? camY : eyeY);
		double dz = pos.getZ() + 0.5 - (camSampled ? camZ : eyeZ);
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (c.blockEntityBudget > 0 && inFront(dx, dy, dz)) {
			blockBudget.add(dist);
		}
		if (dist <= SAFE_RADIUS || EXEMPT_BLOCK_ENTITY_TYPE.get(be.getClass())) {
			return false;
		}

		double max = Math.max(SAFE_RADIUS, c.blockEntityMaxDistance * (0.5 + 0.5 * effScale()));
		boolean cull = dist > max || (c.blockEntityBudget > 0 && dist > blockBudget.cutoff());
		if (cull) {
			count(C_BLOCK);
		}
		return cull;
	}

	private static boolean cullShadowBlockEntity(BlockEntity be, Config c) {
		if (!lookValid) {
			return false;
		}
		BlockPos pos = be.getBlockPos();
		double dx = pos.getX() + 0.5 - eyeX;
		double dy = pos.getY() + 0.5 - eyeY;
		double dz = pos.getZ() + 0.5 - eyeZ;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist <= SAFE_RADIUS * 2 || EXEMPT_BLOCK_ENTITY_TYPE.get(be.getClass())) {
			return false;
		}
		boolean cull = dist > c.shadowPassDistance * (0.5 + 0.5 * effScale());
		if (cull) {
			count(C_BLOCK);
		}
		return cull;
	}

	// ---- particles --------------------------------------------------------------------------

	/** @return true when this particle quad should not be submitted */
	public static boolean cullParticle(Object particle) {
		hook(HOOK_PARTICLE);
		Config c = Config.get();

		// without a frame timer there is no per-frame reset, so a budget would starve everything
		if (!c.enabled || !c.particleLimit || !hooked[HOOK_FRAME]) {
			return false;
		}

		if (ShaderCompat.shadowPass()) {
			return false;
		}
		float s = effScale();
		if (s < 0.999f) {
			int bucket = (System.identityHashCode(particle) * 0x9E3779B1) >>> 25; // 0..127, stable per particle
			if (bucket >= (int) (128 * (0.35f + 0.65f * s))) {
				count(C_PARTICLE);
				return true;
			}
		}

		if (camSampled && ParticleAccess.available()) {
			double dx = ParticleAccess.x(particle) - camX;
			double dy = ParticleAccess.y(particle) - camY;
			double dz = ParticleAccess.z(particle) - camZ;
			double dot = dx * lookX + dy * lookY + dz * lookZ;

			if (c.particleBehindCull && !thirdPerson && dot < -2.0) {
				count(C_PARTICLE);
				return true;
			}

			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > Math.max(SAFE_PARTICLE_RADIUS, c.particleMaxDistance * (0.5 + 0.5 * s))) {
				count(C_PARTICLE);
				return true;
			}
			if (thirdPerson || dot > 0) {
				particleBudget.add(dist);
			}
			if (dist > SAFE_PARTICLE_RADIUS && dist > particleBudget.cutoff()) {
				count(C_PARTICLE);
				return true;
			}
		}

		// last resort, also the only limit when particle positions cannot be read
		if (++particlesThisFrame > c.particleBudget * Math.max(0.35f, s) * 1.25f) {
			count(C_PARTICLE);
			return true;
		}
		return false;
	}

	// ---- explosions and sound ---------------------------------------------------------------

	public static boolean allowExplosionEffects() {
		hook(HOOK_EXPLOSION);
		Config c = Config.get();
		if (!c.enabled || c.explosionEffectCap <= 0) {
			return true;
		}
		if (++explosionEffects > c.explosionEffectCap) {
			count(C_EXPLOSION);
			return false;
		}
		return true;
	}

	public static boolean allowSound(Object soundEvent) {
		hook(HOOK_SOUND);
		Config c = Config.get();
		if (!c.enabled || c.explosionSoundCap <= 0 || soundEvent == null) {
			return true;
		}
		Boolean explode = explodeSounds.get(soundEvent);
		if (explode == null) {
			if (explodeSounds.size() > 512) {
				explodeSounds.clear();
			}
			explode = soundEvent.toString().contains("explode");
			explodeSounds.put(soundEvent, explode);
		}
		if (explode && ++explosionSounds > c.explosionSoundCap) {
			count(C_SOUND);
			return false;
		}
		return true;
	}

	// ---- animated textures ------------------------------------------------------------------

	public static boolean skipTextureAnimation() {
		hook(HOOK_TEXTURE);
		Config c = Config.get();
		int d = c.textureAnimationDivisor;
		if (!c.enabled || d <= 1) {
			return false;
		}
		if (++animCalls % d != 0) {
			count(C_ANIM);
			return true;
		}
		return false;
	}

	// ---- text for the HUD and Info tab ---------------------------------------------------------

	private static long perFrame(int which) {
		return Math.round((double) shown[which] / framesShown);
	}

	private static String cut(Budget b) {
		return b.active() ? String.valueOf(Math.round(b.cutoff())) : "-";
	}

	public static String[] hudLines() {
		Config c = Config.get();
		List<String> lines = new ArrayList<>(3);
		lines.add(String.format(Locale.ROOT, "Kestrel%s%s  %d fps  low %d  %.1f ms  LOD %d%%",
				c.enabled ? "" : " (off)", shadersOn ? " [shaders]" : "", Math.round(fps()), Math.round(lowFps),
				emaNanos / 1.0e6, scalePercent()));
		lines.add(String.format(Locale.ROOT, "skipped/frame  ent %d  part %d  be %d",
				perFrame(C_ENTITY), perFrame(C_PARTICLE), perFrame(C_BLOCK)));
		if (c.hudExtended) {
			Runtime rt = Runtime.getRuntime();
			long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
			lines.add(String.format(Locale.ROOT, "heap %d/%d MB  cutoff e %s  p %s  b %s",
					used, rt.maxMemory() >> 20, cut(entityBudget), cut(particleBudget), cut(blockBudget)));
		}
		return lines.toArray(new String[0]);
	}

	public static int hudColor() {
		double fps = fps();
		int target = Config.get().targetFps;
		if (fps >= target) {
			return 0xFF55FF55;
		}
		return fps >= target * 0.7 ? 0xFFFFFF55 : 0xFFFF5555;
	}

	public static String hookSummary() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < HOOK_NAMES.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(HOOK_NAMES[i]).append(hooked[i] ? " ok" : " (not seen yet)");
		}
		return sb.toString();
	}

	public static List<String> diagnostics() {
		Runtime rt = Runtime.getRuntime();
		List<String> out = new ArrayList<>();
		out.add(String.format(Locale.ROOT, "fps %d (1%% low %d), LOD %d%%, master %s, preset %s",
				Math.round(fps()), Math.round(lowFps), scalePercent(), Config.get().enabled ? "on" : "off", Presets.current()));
		out.add("cutoffs: entities " + cut(entityBudget) + ", particles " + cut(particleBudget)
				+ ", block entities " + cut(blockBudget) + (thirdPerson ? " (third person view)" : ""));
		out.add("last second per frame: entities " + perFrame(C_ENTITY) + ", particles " + perFrame(C_PARTICLE)
				+ ", block entities " + perFrame(C_BLOCK) + "; ticks " + shown[C_TICK] + ", sounds " + shown[C_SOUND]
				+ ", explosions " + shown[C_EXPLOSION]);
		out.add("threads: " + Threads.describe());
		out.add(String.format(Locale.ROOT, "heap %d/%d MB, %d cores, particle positions %s",
				(rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20, rt.availableProcessors(),
				ParticleAccess.available() ? "readable" : "NOT readable"));
		out.add("shader pack: " + (!ShaderCompat.installed() ? "Iris not installed" : shadersOn ? "active, budgets x" + Math.round(shaderFactor * 100) + "%" : "none active"));
		return out;
	}

	public static List<String> hookLines() {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < HOOK_NAMES.length; i++) {
			out.add("hook " + HOOK_NAMES[i] + ": " + (hooked[i] ? "ok" : "not seen yet"));
		}
		return out;
	}

	/** Everything the Info tab shows. */
	public static List<String> infoLines() {
		List<String> out = diagnostics();
		out.addAll(hookLines());
		return out;
	}
}
