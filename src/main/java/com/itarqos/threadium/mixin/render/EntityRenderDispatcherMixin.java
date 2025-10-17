package com.itarqos.threadium.mixin.render;

import com.itarqos.threadium.client.ThreadiumClient;
import com.itarqos.threadium.util.CullingStats;
import com.itarqos.threadium.util.CullingUtil;
import com.itarqos.threadium.util.MovementPredictor;
import com.itarqos.threadium.util.FrameBudgetController;
import com.itarqos.threadium.util.ThreadiumLog;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void threadium$entityCulling(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (ThreadiumClient.CONFIG == null || !ThreadiumClient.CONFIG.enableEntityCulling) return;

        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.getCameraEntity() == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d camPos = mc.getCameraEntity().getCameraPosVec(tickDelta);
        if (ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enablePredictionEverywhere) {
            camPos = MovementPredictor.get().getPredictedCamPos(Math.max(0, ThreadiumClient.CONFIG.predictionAheadTicks));
        }
        Vec3d forward = MovementPredictor.get().getSmoothedForward();
        Vec3d entPos = entity.getLerpedPos(tickDelta);
        Vec3d toEntity = entPos.subtract(camPos);

        // Vertical band culling
        if (ThreadiumClient.CONFIG.enableEntityVerticalBandCulling) {
            int half = Math.max(1, ThreadiumClient.CONFIG.verticalBandHalfHeight);
            double dyAbs = Math.abs(entPos.y - camPos.y);
            if (dyAbs > half) {
                CullingStats.incEntityCulled();
                ThreadiumLog.debug("Entity culled by Y-band: %s at Y=%.1f (camera Y=%.1f, dyAbs=%.1f > %d)", 
                    entity.getType().getName().getString(), entPos.y, camPos.y, dyAbs, half);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }
        }

        // Dynamic thresholds using predictor
        double speed = MovementPredictor.get().getSmoothedSpeed();
        double frontMax = 64.0 + Math.min(48.0, speed * 96.0);
        double behindMax = Math.max(8.0, 16.0 - Math.min(12.0, speed * 24.0));
        double behindAngle = 125.0;
        if (!ThreadiumClient.CONFIG.enableEntityBehindCulling) {
            // Effectively disable behind-specific culling
            behindMax = Double.MAX_VALUE;
            behindAngle = 180.0;
        }
        // Apply QoS scaling
        double cutoffMult = FrameBudgetController.get().getFarCutoffMultiplier();
        frontMax *= cutoffMult;
        behindMax *= cutoffMult;
        boolean cull = CullingUtil.shouldCullByAngleAndDistance(
                camPos,
                forward,
                entPos,
                frontMax,
                behindMax,
                behindAngle
        );
        if (cull) {
            CullingStats.incEntityCulled();
            ThreadiumLog.debug("Entity culled by angle/distance: %s at distance=%.1f", 
                entity.getType().getName().getString(), toEntity.length());
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // Simple LOD: reduce update frequency for far entities
        if (ThreadiumClient.CONFIG.lodThrottlingEnabled) {
            double dist = toEntity.length();
            long tick = mc.world != null ? mc.world.getTime() : 0L;
            int level = FrameBudgetController.get().getLodLevel();
            double farBand = level == 2 ? 32.0 : (level == 1 ? 40.0 : 48.0);
            double midBand = level == 2 ? 16.0 : (level == 1 ? 20.0 : 24.0);
            // Skip cadence increases with level
            if (dist > farBand) {
                int mask = (level == 2) ? 5 : (level == 1 ? 3 : 3); // 1-in-6 (mask=5), else 1-in-4
                if ((tick & mask) != 0L) {
                    CullingStats.incEntityCulled();
                    ThreadiumLog.debug("Entity LOD throttled (far L%d): %s d=%.1f", level, entity.getType().getName().getString(), dist);
                    cir.setReturnValue(false);
                    cir.cancel();
                    return;
                }
            } else if (dist > midBand) {
                int mask = (level >= 1) ? 1 : 1; // >=L1: still every other frame
                if ((tick & mask) != 0L) {
                    CullingStats.incEntityCulled();
                    ThreadiumLog.debug("Entity LOD throttled (mid L%d): %s d=%.1f", level, entity.getType().getName().getString(), dist);
                    cir.setReturnValue(false);
                    cir.cancel();
                    return;
                }
            }
        }

        // Vanilla frustum checks will continue as normal.
    }
}
