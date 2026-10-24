package dev.kestrel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kestrel.Lod;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both playLocalSound overloads take a SoundEvent, so one handler covers them. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel", remap = false)
public abstract class ClientLevelSoundMixin {
	@Inject(method = "playLocalSound", at = @At("HEAD"), cancellable = true, remap = false)
	private void kestrel$sound(CallbackInfo ci, @Local(argsOnly = true) SoundEvent event) {
		if (!Lod.allowSound(event)) {
			ci.cancel();
		}
	}
}
