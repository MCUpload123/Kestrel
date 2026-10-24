package dev.kestrel.mixin;

import dev.kestrel.Lod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client side particle storm of an explosion; knockback and damage are handled elsewhere. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel", remap = false)
public abstract class ClientLevelExplosionMixin {
	@Inject(method = "trackExplosionEffects", at = @At("HEAD"), cancellable = true, remap = false)
	private void kestrel$explosion(CallbackInfo ci) {
		if (!Lod.allowExplosionEffects()) {
			ci.cancel();
		}
	}
}
