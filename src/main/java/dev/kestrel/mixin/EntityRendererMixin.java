package dev.kestrel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kestrel.Lod;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EntityRenderer.shouldRender(T, Frustum, double, double, double); arguments are read with @Local. */
@Mixin(targets = "net.minecraft.client.renderer.entity.EntityRenderer", remap = false)
public abstract class EntityRendererMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, remap = false)
	private void kestrel$cull(CallbackInfoReturnable<Boolean> cir,
							  @Local(argsOnly = true) Entity entity,
							  @Local(argsOnly = true, ordinal = 0) double camX,
							  @Local(argsOnly = true, ordinal = 1) double camY,
							  @Local(argsOnly = true, ordinal = 2) double camZ) {
		if (Lod.cullEntity(entity, camX, camY, camZ)) {
			cir.setReturnValue(false);
		}
	}
}
