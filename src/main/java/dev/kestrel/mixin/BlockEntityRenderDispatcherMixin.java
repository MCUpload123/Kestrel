package dev.kestrel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kestrel.Lod;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** tryExtractRenderState returns null when there is nothing to draw, so null is how a block entity is skipped. */
@Mixin(targets = "net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher", remap = false)
public abstract class BlockEntityRenderDispatcherMixin {
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true, remap = false)
	private void kestrel$cull(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) BlockEntity blockEntity) {
		if (Lod.cullBlockEntity(blockEntity)) {
			cir.setReturnValue(null);
		}
	}
}
