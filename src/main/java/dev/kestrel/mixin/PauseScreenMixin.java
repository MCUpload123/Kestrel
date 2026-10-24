package dev.kestrel.mixin;

import dev.kestrel.ScreenButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Kestrel button once the vanilla widgets of PauseScreen are built. */
@Mixin(targets = "net.minecraft.client.gui.screens.PauseScreen", remap = false)
public abstract class PauseScreenMixin {
	@Inject(method = "init", at = @At("TAIL"), remap = false)
	private void kestrel$button(CallbackInfo ci) {
		ScreenButton.add(this);
	}
}
