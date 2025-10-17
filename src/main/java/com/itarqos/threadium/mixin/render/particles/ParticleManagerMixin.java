package com.itarqos.threadium.mixin.render.particles;

import com.itarqos.threadium.client.particles.OptimizedParticleSystem;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Use a stable injection point to drive the optimized particle system tick.
 * Rendering hook will be added separately against a stable target.
 */
@Mixin(MinecraftClient.class)
public class ParticleManagerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void threadium$tickOptimizedParticles(CallbackInfo ci) {
        OptimizedParticleSystem.get().tick();
    }
}
