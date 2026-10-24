package dev.kestrel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kestrel.Lod;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ClientLevel.tickNonPassenger(Entity): far TNT / items / xp / falling blocks tick less often. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel", remap = false)
public abstract class ClientLevelTickMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true, remap = false)
	private void kestrel$throttle(CallbackInfo ci, @Local(argsOnly = true) Entity entity) {
		if (Lod.skipEntityTick(entity)) {
			ci.cancel();
		}
	}
}
