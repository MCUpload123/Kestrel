package dev.kestrel.mixin;

import dev.kestrel.Lod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.texture.TextureAtlas", remap = false)
public abstract class TextureAtlasMixin {
	@Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true, remap = false)
	private void kestrel$throttle(CallbackInfo ci) {
		if (Lod.skipTextureAnimation()) {
			ci.cancel();
		}
	}
}
