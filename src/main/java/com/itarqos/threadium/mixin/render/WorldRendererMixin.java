package com.itarqos.threadium.mixin.render;

import com.itarqos.threadium.world.SubIdentifierManager;
import com.itarqos.threadium.util.CullingUtil;
import com.itarqos.threadium.util.MovementPredictor;
import com.itarqos.threadium.util.ThreadiumLog;
import com.itarqos.threadium.client.particles.OptimizedParticleSystem;
import com.itarqos.threadium.client.ThreadiumClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "scheduleBlockRerenderIfNeeded", at = @At("HEAD"), cancellable = true)
    private void threadium$deferRerenderIfOccluded(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        // Track dirty slice and maintain non-air counts
        SubIdentifierManager.get().onBlockStateChanged(pos, oldState.isAir(), newState.isAir());
        ThreadiumLog.debug("Block state changed at %s: %s -> %s", pos, oldState, newState);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getCameraEntity() == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d camPos = mc.getCameraEntity().getCameraPosVec(tickDelta);
        if (ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enablePredictionEverywhere) {
            camPos = MovementPredictor.get().getPredictedCamPos(Math.max(0, ThreadiumClient.CONFIG.predictionAheadTicks));
        }
        Vec3d forward = MovementPredictor.get().getSmoothedForward();
        Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        double speed = MovementPredictor.get().getSmoothedSpeed();
        double frontMax = 64.0 + Math.min(48.0, speed * 96.0);
        double behindMax = Math.max(12.0, 32.0 - Math.min(16.0, speed * 24.0));
        double farCutoff = 98.0 + Math.min(32.0, speed * 48.0);

        boolean culledBehind = CullingUtil.shouldCullByAngleAndDistance(camPos, forward, target, frontMax, behindMax, 125.0);
        boolean tooFar = target.distanceTo(camPos) > farCutoff;

        if (culledBehind || tooFar) {
            // Defer render until visible; our tick hook will reschedule later
            ThreadiumLog.debug("Deferred block rerender at %s (culledBehind=%s, tooFar=%s)", pos, culledBehind, tooFar);
            ci.cancel();
        }
    }

    @Inject(method = "scheduleSectionRender", at = @At("HEAD"), cancellable = true)
    private void threadium$cancelSectionRenderIfOccluded(BlockPos pos, boolean rerenderOnNextFrame, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getCameraEntity() == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d camPos = mc.getCameraEntity().getCameraPosVec(tickDelta);
        if (ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enablePredictionEverywhere) {
            camPos = MovementPredictor.get().getPredictedCamPos(Math.max(0, ThreadiumClient.CONFIG.predictionAheadTicks));
        }
        Vec3d forward = MovementPredictor.get().getSmoothedForward();
        Vec3d target = new Vec3d(pos.getX() + 8.0, pos.getY() + 8.0, pos.getZ() + 8.0); // approximate section center

        double speed = MovementPredictor.get().getSmoothedSpeed();
        double frontMax = 64.0 + Math.min(48.0, speed * 96.0);
        double behindMax = Math.max(12.0, 32.0 - Math.min(16.0, speed * 24.0));
        double farCutoff = 98.0 + Math.min(32.0, speed * 48.0);

        boolean culledBehind = CullingUtil.shouldCullByAngleAndDistance(camPos, forward, target, frontMax, behindMax, 125.0);
        boolean tooFar = target.distanceTo(camPos) > farCutoff;

        if (culledBehind || tooFar) {
            ThreadiumLog.debug("Cancelled section render at %s (culledBehind=%s, tooFar=%s, distance=%.1f)",
                    pos, culledBehind, tooFar, target.distanceTo(camPos));
            ci.cancel();
        }
    }

    // Mark sections as visible when the render scheduling proceeds (not cancelled)
    @Inject(method = "scheduleSectionRender", at = @At("TAIL"))
    private void threadium$markSectionVisible(BlockPos pos, boolean rerenderOnNextFrame, CallbackInfo ci) {
        SubIdentifierManager.get().markSectionVisible(pos);
    }

    // Stable render tail hook: call optimized particle system's render after world is rendered
    @Inject(method = "render", at = @At("TAIL"))
    private void threadium$renderOptimizedParticles(ObjectAllocator allocator,
                                                    RenderTickCounter tickCounter,
                                                    boolean renderBlockOutline,
                                                    Camera camera,
                                                    GameRenderer gameRenderer,
                                                    Matrix4f modelViewMatrix,
                                                    Matrix4f projectionMatrix,
                                                    CallbackInfo ci) {
        OptimizedParticleSystem.get().render(tickCounter.getTickDelta(false));
    }
}
