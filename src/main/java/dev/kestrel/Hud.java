package dev.kestrel;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Small stats box, top right. The lambda parameter types come from HudElement, so nothing else to import. */
public final class Hud {
	private Hud() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("kestrel", "stats"), (graphics, tickCounter) -> {
			Lod.onHudFrame();
			if (!Config.get().hud) {
				return;
			}

			Minecraft mc = Minecraft.getInstance();
			String[] lines = Lod.hudLines();
			int width = 0;
			for (String line : lines) {
				width = Math.max(width, mc.font.width(line));
			}

			int x = mc.getWindow().getGuiScaledWidth() - width - 6;
			int y = 4;
			graphics.fill(x - 3, y - 2, x + width + 3, y + lines.length * 10, 0x80000000);

			int color = Lod.hudColor();
			for (int i = 0; i < lines.length; i++) {
				graphics.text(mc.font, lines[i], x, y + i * 10, color, false);
			}
		});
	}
}
