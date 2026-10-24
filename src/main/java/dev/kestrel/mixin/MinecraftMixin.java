package dev.kestrel.mixin;

import dev.kestrel.Lod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.Minecraft", remap = false)
public abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"), remap = false)
	private void kestrel$frame(CallbackInfo ci) {
		Lod.onRunTick();
	}
}
