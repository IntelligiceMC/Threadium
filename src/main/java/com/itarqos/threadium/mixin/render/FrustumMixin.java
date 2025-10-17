package com.itarqos.threadium.mixin.render;

import com.itarqos.threadium.client.ThreadiumClient;
import com.itarqos.threadium.util.MovementPredictor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Frustum.class)
public class FrustumMixin {

    @Unique
    private static final java.util.Map<Long, Long> threadium$recentVisibleTick = new java.util.HashMap<>();

    @Unique
    private static long threadium$keyFor(Box box) {
        // Quantize center and size to form a stable key
        Vec3d c = box.getCenter();
        long cx = Math.round(c.x * 2.0);
        long cy = Math.round(c.y * 2.0);
        long cz = Math.round(c.z * 2.0);
        long sx = Math.round((box.getLengthX()) * 2.0);
        long sy = Math.round((box.getLengthY()) * 2.0);
        long sz = Math.round((box.getLengthZ()) * 2.0);
        long h = 1469598103934665603L;
        h ^= cx; h *= 1099511628211L;
        h ^= cy; h *= 1099511628211L;
        h ^= cz; h *= 1099511628211L;
        h ^= sx; h *= 1099511628211L;
        h ^= sy; h *= 1099511628211L;
        h ^= sz; h *= 1099511628211L;
        return h;
    }

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void threadium$verticalBandChunkCulling(Box box, CallbackInfoReturnable<Boolean> cir) {
        if (ThreadiumClient.CONFIG == null) return;
        if (!ThreadiumClient.CONFIG.enableChunkCulling) return;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getCameraEntity() == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d camPos = mc.getCameraEntity().getCameraPosVec(tickDelta);
        Vec3d forward = MovementPredictor.get().getSmoothedForward();

        // Existing optional vertical band constraint (with dynamic breadth)
        if (ThreadiumClient.CONFIG.enableChunkVerticalBandCulling) {
            int baseHalf = Math.max(1, ThreadiumClient.CONFIG.verticalBandHalfHeight);
            int half = baseHalf;
            if (ThreadiumClient.CONFIG.enableDynamicVerticalBand) {
                double spd = MovementPredictor.get().getSmoothedSpeed();
                // widen by up to +6 blocks with speed
                half += Math.min(6, (int) Math.round(spd * 12.0));
                // widen by pitch (looking up/down)
                float pitch = mc.getCameraEntity().getPitch(); // -90..90
                half += Math.min(4, (int) Math.round(Math.abs(pitch) / 90.0 * 4.0));
            }
            double minY = camPos.y - half;
            double maxY = camPos.y + half;
            if (box.maxY < minY || box.minY > maxY) {
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }
        }

        // Dynamic thresholds using predictor
        double speed = MovementPredictor.get().getSmoothedSpeed();
        double behindAngle = 125.0;
        double behindMax = Math.max(16.0, 32.0 - Math.min(16.0, speed * 24.0));
        double farCutoff = 98.0 + Math.min(32.0, speed * 48.0);

        // Center of the box for distance and angle checks
        Vec3d center = box.getCenter();
        Vec3d to = center.subtract(camPos);
        double dist = to.length();
        if (dist < 1e-4) return;
        Vec3d toNorm = to.multiply(1.0 / dist);
        double dot = forward.dotProduct(toNorm);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angleDeg = Math.toDegrees(Math.acos(dot));

        // Do not render blocks behind the player beyond the dynamic behindMax
        long key = threadium$keyFor(box);
        long worldTick = mc.world != null ? mc.world.getTime() : 0L;
        int hyst = Math.max(0, ThreadiumClient.CONFIG.frustumHysteresisTicks);
        if (angleDeg > behindAngle && dist > behindMax) {
            Long lastVis = threadium$recentVisibleTick.get(key);
            if (lastVis != null && (worldTick - lastVis) <= hyst) {
                // keep visible for hysteresis window
                return;
            }
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // General cutoff for very far chunk/box content (dynamic)
        if (dist > farCutoff) {
            Long lastVis = threadium$recentVisibleTick.get(key);
            if (lastVis != null && (worldTick - lastVis) <= hyst) {
                // keep visible for hysteresis window
                return;
            }
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // Mark visible this tick
        threadium$recentVisibleTick.put(key, worldTick);
    }
}
