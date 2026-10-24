package dev.kestrel;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Kestrel button that the screen mixins put in the top left corner of the title, pause and options screens. */
public final class ScreenButton {
	private ScreenButton() {
	}

	public static void add(Object target) {
		if (!Config.get().uiButton || !(target instanceof Screen screen)) {
			return;
		}
		Button button = Button.builder(Component.literal("Kestrel"), b -> Compat.show(new KestrelScreen(screen)))
				.bounds(4, 4, 56, 20)
				.build();
		Compat.addWidget(screen, button);
	}
}
