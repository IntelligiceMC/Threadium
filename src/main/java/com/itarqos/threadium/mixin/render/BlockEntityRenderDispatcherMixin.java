package com.itarqos.threadium.mixin.render;

import com.itarqos.threadium.client.ThreadiumClient;
import com.itarqos.threadium.util.CullingStats;
import com.itarqos.threadium.util.CullingUtil;
import com.itarqos.threadium.util.MovementPredictor;
import com.itarqos.threadium.util.FrameBudgetController;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void threadium$cullBlockEntity(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                                           VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (ThreadiumClient.CONFIG == null) return;
        if (!ThreadiumClient.CONFIG.enableBlockEntityCulling) return;
        if (blockEntity == null) return;

        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.getCameraEntity() == null) return;

        Vec3d camPos = mc.getCameraEntity().getCameraPosVec(tickDelta);
        if (ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enablePredictionEverywhere) {
            camPos = MovementPredictor.get().getPredictedCamPos(Math.max(0, ThreadiumClient.CONFIG.predictionAheadTicks));
        }
        Vec3d forward = MovementPredictor.get().getSmoothedForward();
        BlockPos bp = blockEntity.getPos();
        Vec3d bePos = new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);

        // Vertical band culling
        if (ThreadiumClient.CONFIG.enableEntityVerticalBandCulling) {
            int half = Math.max(1, ThreadiumClient.CONFIG.verticalBandHalfHeight);
            if (Math.abs(bePos.y - camPos.y) > half) {
                CullingStats.incBlockEntityCulled();
                ci.cancel();
                return;
            }
        }

        // Dynamic thresholds using predictor
        double speed = MovementPredictor.get().getSmoothedSpeed();
        double frontMax = 64.0 + Math.min(48.0, speed * 96.0);
        double behindMax = Math.max(8.0, 16.0 - Math.min(12.0, speed * 24.0));
        double behindAngle = 125.0;
        if (!ThreadiumClient.CONFIG.enableEntityBehindCulling) {
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
                bePos,
                frontMax,
                behindMax,
                behindAngle
        );
        if (cull) {
            CullingStats.incBlockEntityCulled();
            ci.cancel();
            return;
        }

        // Simple LOD: reduce update frequency for far block entities
        if (ThreadiumClient.CONFIG.lodThrottlingEnabled) {
            double dist = bePos.subtract(camPos).length();
            long tick = mc.world != null ? mc.world.getTime() : 0L;
            int level = FrameBudgetController.get().getLodLevel();
            double farBand = level == 2 ? 32.0 : (level == 1 ? 40.0 : 48.0);
            double midBand = level == 2 ? 16.0 : (level == 1 ? 20.0 : 24.0);
            if (dist > farBand) {
                int mask = (level == 2) ? 5 : (level == 1 ? 3 : 3);
                if ((tick & mask) != 0L) {
                    CullingStats.incBlockEntityCulled();
                    ci.cancel();
                    return;
                }
            } else if (dist > midBand) {
                int mask = (level >= 1) ? 1 : 1;
                if ((tick & mask) != 0L) {
                    CullingStats.incBlockEntityCulled();
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
