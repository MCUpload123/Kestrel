package dev.kestrel;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class KestrelClient implements ClientModInitializer {
	private static KeyMapping toggleHud;
	private static KeyMapping cyclePreset;
	private static KeyMapping openSettings;

	@Override
	public void onInitializeClient() {
		Config.load();
		Lod.announce();

		Hud.register();
		registerKeys();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Lod.onDisconnect());
		ClientTickEvents.END_CLIENT_TICK.register(KestrelClient::onTick);
	}

	private static void registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("kestrel", "main"));
		toggleHud = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.kestrel.toggle_hud", InputConstants.KEY_F8, category));
		cyclePreset = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.kestrel.cycle_preset", InputConstants.KEY_F9, category));
		openSettings = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.kestrel.open_settings", InputConstants.KEY_K, category));
	}

	private static void onTick(Minecraft client) {
		Lod.onClientTick();
		while (toggleHud.consumeClick()) {
			Config.get().hud = !Config.get().hud;
			Config.save();
			tell(client, "stats box " + (Config.get().hud ? "on" : "off"));
		}
		while (openSettings.consumeClick()) {
			Compat.show(new KestrelScreen(null));
		}
		while (cyclePreset.consumeClick()) {
			String next = Presets.next();
			Presets.apply(next);
			Config.save();
			tell(client, "preset " + next);
		}
	}

	private static void tell(Minecraft client, String message) {
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[Kestrel] " + message));
		}
	}
}
