package dev.kestrel.mixin;

import dev.kestrel.Lod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.particle.SingleQuadParticle", remap = false)
public abstract class SingleQuadParticleMixin {
	@Inject(method = "extract", at = @At("HEAD"), cancellable = true, remap = false)
	private void kestrel$limit(CallbackInfo ci) {
		if (Lod.cullParticle(this)) {
			ci.cancel();
		}
	}
}
