package dev.kestrel;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The settings screen. Built only from buttons so it does not depend on text rendering or slider APIs:
 * tabs on top, two columns of settings, page buttons at the bottom. Everything saves as you click.
 * Number settings: "-" and "+" change by about 10% of the value, clicking the value resets it.
 */
public final class KestrelScreen extends Screen {
	private static final String[] TABS = {"Presets", "Entities", "Mobs", "World", "FPS", "Info"};
	private static final int PRESETS = 0;
	private static final int ENTITIES = 1;
	private static final int MOBS = 2;
	private static final int WORLD = 3;
	private static final int FPS = 4;
	private static final int INFO = 5;
	private static final int ROW = 22;

	private static final String[][] MOB_NAMES = {
			{"zombie", "Zombie"}, {"skeleton", "Skeleton"}, {"creeper", "Creeper"}, {"spider", "Spider"},
			{"enderman", "Enderman"}, {"villager", "Villager"}, {"cow", "Cow"}, {"sheep", "Sheep"},
			{"pig", "Pig"}, {"chicken", "Chicken"}, {"armorstand", "Armor stand"}, {"itemframe", "Item frame"},
			{"boat", "Boat"}, {"minecart", "Minecart"}};

	private static final Map<String, String> LABELS = Map.ofEntries(
			Map.entry("enabled", "Kestrel master switch"),
			Map.entry("entity_culling", "Entity distance culling"),
			Map.entry("entity_max_distance", "Entity distance"),
			Map.entry("entity_budget", "Entity budget"),
			Map.entry("tnt_lod", "TNT LOD"),
			Map.entry("tnt_full", "TNT full detail"),
			Map.entry("tnt_cut", "TNT cut-off"),
			Map.entry("small_entity_lod", "Item/XP LOD"),
			Map.entry("small_full", "Item/XP full detail"),
			Map.entry("small_cut", "Item/XP cut-off"),
			Map.entry("entity_tick_throttle", "Far entity tick throttle"),
			Map.entry("entity_tick_distance", "Tick throttle distance"),
			Map.entry("entity_tick_divisor", "Tick every N ticks"),
			Map.entry("block_entity_culling", "Block entity culling"),
			Map.entry("block_entity_max_distance", "Block entity distance"),
			Map.entry("block_entity_budget", "Block entity budget"),
			Map.entry("particle_limit", "Particle limiter"),
			Map.entry("particle_budget", "Particle budget"),
			Map.entry("particle_max_distance", "Particle distance"),
			Map.entry("particle_behind_cull", "Skip particles behind you"),
			Map.entry("texture_animation_divisor", "Texture animation 1/N"),
			Map.entry("explosion_effect_cap", "Explosion effects/tick"),
			Map.entry("explosion_sound_cap", "Explosion sounds/tick"),
			Map.entry("adaptive", "FPS governor"),
			Map.entry("target_fps", "Target FPS"),
			Map.entry("min_scale_percent", "Governor minimum %"),
			Map.entry("adaptive_render_distance", "Auto render distance"),
			Map.entry("min_render_distance", "Minimum render dist."),
			Map.entry("menu_fps_cap", "FPS cap in menus"),
			Map.entry("unfocused_fps_cap", "FPS cap unfocused"),
			Map.entry("thread_priorities", "Thread priorities"),
			Map.entry("render_thread_priority", "Render thread priority"),
			Map.entry("server_thread_priority", "Server thread priority"),
			Map.entry("worker_thread_priority", "Worker thread priority"),
			Map.entry("gc_on_disconnect", "GC when leaving a world"),
			Map.entry("shader_mode", "Shader pack mode"),
			Map.entry("shader_scale_percent", "Shader scale %"),
			Map.entry("shadow_pass_distance", "Shadow pass distance"),
			Map.entry("auto_tune", "Auto tune on first join"),
			Map.entry("hud", "Stats box"),
			Map.entry("hud_extended", "Stats box: heap line"),
			Map.entry("ui_button", "Kestrel menu button"));

	private static int rememberedTab;

	private final Screen parent;
	private int tab = rememberedTab;
	private int page;
	private String status = "";
	private int ticks;

	private interface Row {
		void add(int x, int y, int w);
	}

	public KestrelScreen(Screen parent) {
		super(Component.literal("Kestrel"));
		this.parent = parent;
	}

	// ---- layout -----------------------------------------------------------------------------

	protected void init() {
		int cx = this.width / 2;

		addRenderableWidget(label(Component.literal("Kestrel performance settings"), cx - 120, 4, 240));

		int tabW = Math.min(96, (this.width - 16) / TABS.length);
		int tabsLeft = (this.width - tabW * TABS.length) / 2;
		for (int i = 0; i < TABS.length; i++) {
			final int index = i;
			String text = i == tab ? "> " + TABS[i] + " <" : TABS[i];
			addRenderableWidget(Button.builder(Component.literal(text), b -> {
				tab = index;
				rememberedTab = index;
				page = 0;
				rebuild();
			}).bounds(tabsLeft + i * tabW, 28, tabW, 20).build());
		}

		List<Row> rows = rowsFor(tab);
		int cols = tab == INFO ? 1 : 2;
		int top = 56;
		int bottom = this.height - 56;
		int perColumn = Math.max(1, (bottom - top) / ROW);
		int perPage = perColumn * cols;
		int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));

		int colW = cols == 1 ? Math.min(380, this.width - 40) : Math.min(210, (this.width - 24) / 2);
		int totalW = colW * cols + (cols - 1) * 8;
		int left = (this.width - totalW) / 2;
		for (int n = 0; n < perPage; n++) {
			int index = page * perPage + n;
			if (index >= rows.size()) {
				break;
			}
			rows.get(index).add(left + (n % cols) * (colW + 8), top + (n / cols) * ROW, colW);
		}

		String shownStatus = status.isEmpty() ? "Changes are saved as you click" : status;
		addRenderableWidget(label(Component.literal(shownStatus), cx - 150, this.height - 50, 300));

		int navY = this.height - 26;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			rebuild();
		}).bounds(cx - 150, navY, 40, 20).build());
		addRenderableWidget(label(Component.literal("Page " + (page + 1) + "/" + pages), cx - 106, navY, 80));
		addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			rebuild();
		}).bounds(cx - 22, navY, 40, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(cx + 30, navY, 120, 20).build());
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	/** Called every client tick while the screen is open; keeps the Info tab live. */
	public void tick() {
		if (tab == INFO && ++ticks % 20 == 0) {
			rebuild();
		}
	}

	public void onClose() {
		Config.save();
		Compat.show(parent);
	}

	// ---- tabs -------------------------------------------------------------------------------

	private List<Row> rowsFor(int which) {
		List<Row> rows = new ArrayList<>();
		switch (which) {
			case PRESETS -> {
				for (String name : Presets.names()) {
					rows.add(preset(name));
				}
				rows.add(setting("enabled"));
				rows.add(video("potato", "Video options: potato",
						"Lowers the vanilla video options (clouds, particles, AO, mipmaps, blur, ...). A backup is kept."));
				rows.add(video("low", "Video options: low",
						"A milder version of the potato video options. A backup is kept."));
				rows.add(video("restore", "Restore video options", "Puts the video options back as they were before Kestrel changed them."));
				rows.add(setting("auto_tune"));
				rows.add(setting("hud"));
				rows.add(setting("hud_extended"));
				rows.add(setting("ui_button"));
				rows.add(action("Bench 20 s (closes menu)",
						"Records frame times for 20 seconds and prints avg fps, 1% low and the worst frame in chat. Turn Kestrel off/on in between to compare.",
						() -> {
							Bench.start(20);
							Lod.say("bench started, keep playing for 20 seconds");
							onClose();
						}));
				rows.add(action("Run garbage collection", "Frees memory right now.", () -> {
					System.gc();
					status = "Garbage collection requested";
					rebuild();
				}));
			}
			case ENTITIES -> addSettings(rows, "entity_culling", "entity_max_distance", "entity_budget",
					"tnt_lod", "tnt_full", "tnt_cut", "small_entity_lod", "small_full", "small_cut",
					"entity_tick_throttle", "entity_tick_distance", "entity_tick_divisor");
			case MOBS -> {
				for (String[] mob : MOB_NAMES) {
					rows.add(mob(mob[0], mob[1]));
				}
			}
			case WORLD -> addSettings(rows, "block_entity_culling", "block_entity_max_distance", "block_entity_budget",
					"particle_limit", "particle_budget", "particle_max_distance", "particle_behind_cull",
					"texture_animation_divisor", "explosion_effect_cap", "explosion_sound_cap");
			case FPS -> addSettings(rows, "shader_mode", "shader_scale_percent", "shadow_pass_distance",
					"adaptive", "target_fps", "min_scale_percent",
					"adaptive_render_distance", "min_render_distance", "menu_fps_cap", "unfocused_fps_cap",
					"thread_priorities", "render_thread_priority", "server_thread_priority", "worker_thread_priority",
					"gc_on_disconnect");
			default -> {
				for (String line : Lod.infoLines()) {
					rows.add((x, y, w) -> addRenderableWidget(label(Component.literal(line), x, y, w)));
				}
				for (String line : Bench.history()) {
					rows.add((x, y, w) -> addRenderableWidget(label(Component.literal(line), x, y, w)));
				}
			}
		}
		return rows;
	}

	private void addSettings(List<Row> rows, String... keys) {
		for (String key : keys) {
			rows.add(setting(key));
		}
	}

	// ---- rows -------------------------------------------------------------------------------

	private static String name(String key) {
		return LABELS.getOrDefault(key, Config.label(key));
	}

	private static String tip(String key) {
		String doc = Config.doc(key);
		return doc.isEmpty() ? name(key) : doc;
	}

	private Row setting(String key) {
		return Config.isBool(key) ? toggle(key) : number(key);
	}

	private Row toggle(String key) {
		return (x, y, w) -> addRenderableWidget(Button.builder(toggleText(key), b -> {
			Config.set(key, Config.getBool(key) ? "off" : "on");
			Config.save();
			b.setMessage(toggleText(key));
		}).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(tip(key)))).build());
	}

	private static Component toggleText(String key) {
		boolean on = Config.getBool(key);
		return Component.literal(name(key) + ": ")
				.append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private Row number(String key) {
		return (x, y, w) -> {
			Button value = Button.builder(numberText(key), b -> {
				Config.set(key, Config.defaultOf(key));
				Config.save();
				b.setMessage(numberText(key));
			}).bounds(x + 22, y, w - 44, 20)
					.tooltip(Tooltip.create(Component.literal(tip(key) + " (click the value to reset to " + Config.defaultOf(key) + ")")))
					.build();
			addRenderableWidget(Button.builder(Component.literal("-"), b -> step(key, -1, value))
					.bounds(x, y, 20, 20).build());
			addRenderableWidget(value);
			addRenderableWidget(Button.builder(Component.literal("+"), b -> step(key, 1, value))
					.bounds(x + w - 20, y, 20, 20).build());
		};
	}

	private static Component numberText(String key) {
		int v = Config.getInt(key);
		String shown = v == 0 && Config.min(key) == 0 ? "off" : String.valueOf(v);
		return Component.literal(name(key) + ": " + shown);
	}

	/** Moves by about 10% of the value, never less than the setting's own step. */
	private static void step(String key, int direction, Button value) {
		int current = Config.getInt(key);
		int base = Math.max(1, Config.step(key));
		int jump = Math.max(base, Math.round(Math.abs(current) / 10f / base) * base);
		Config.set(key, String.valueOf(current + direction * jump));
		Config.save();
		value.setMessage(numberText(key));
	}

	private Row preset(String presetName) {
		return (x, y, w) -> {
			boolean active = presetName.equals(Presets.current());
			Component text = Component.literal("Preset: " + presetName);
			if (active) {
				text = Component.literal("Preset: " + presetName).withStyle(ChatFormatting.GREEN);
			}
			addRenderableWidget(Button.builder(text, b -> {
				Presets.apply(presetName);
				Config.save();
				status = "Preset " + presetName + " applied";
				rebuild();
			}).bounds(x, y, w, 20)
					.tooltip(Tooltip.create(Component.literal("Sets every Kestrel distance, budget and cap to the " + presetName + " values.")))
					.build());
		};
	}

	private Row video(String which, String text, String tip) {
		return (x, y, w) -> addRenderableWidget(Button.builder(Component.literal(text), b -> {
			status = Tuner.apply(which);
			rebuild();
		}).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(tip))).build());
	}

	private Row action(String text, String tip, Runnable run) {
		return (x, y, w) -> addRenderableWidget(Button.builder(Component.literal(text), b -> run.run())
				.bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(tip))).build());
	}

	private Row mob(String key, String display) {
		return (x, y, w) -> {
			Button value = Button.builder(mobText(key, display), b -> {
				Overrides.set(key, 0);
				Config.save();
				b.setMessage(mobText(key, display));
			}).bounds(x + 22, y, w - 44, 20)
					.tooltip(Tooltip.create(Component.literal(display + " is not drawn beyond this many blocks. Click to follow the global entity distance.")))
					.build();
			addRenderableWidget(Button.builder(Component.literal("-"), b -> moveMob(key, display, -8, value))
					.bounds(x, y, 20, 20).build());
			addRenderableWidget(value);
			addRenderableWidget(Button.builder(Component.literal("+"), b -> moveMob(key, display, 8, value))
					.bounds(x + w - 20, y, 20, 20).build());
		};
	}

	private static void moveMob(String key, String display, int delta, Button value) {
		int current = Overrides.get(key);
		if (current <= 0) {
			current = Config.getInt("entity_max_distance");
		}
		Overrides.set(key, Math.max(16, current + delta));
		Config.save();
		value.setMessage(mobText(key, display));
	}

	private static Component mobText(String key, String display) {
		int v = Overrides.get(key);
		return Component.literal(display + ": " + (v <= 0 ? "global" : v + " blocks"));
	}

	private static Button label(Component text, int x, int y, int w) {
		return Button.builder(text, b -> { }).bounds(x, y, w, 20).build();
	}
}
