package com.itarqos.threadium.util;

import net.minecraft.util.math.Vec3d;

public final class CullingUtil {
    private CullingUtil() {}

    public static boolean shouldCullByAngleAndDistance(Vec3d camPos,
                                                       Vec3d forwardNorm,
                                                       Vec3d targetPos,
                                                       double frontMaxDistance,
                                                       double behindMaxDistance,
                                                       double behindAngleDegrees) {
        if (camPos == null || forwardNorm == null || targetPos == null) return false;
        Vec3d to = targetPos.subtract(camPos);
        double dist = to.length();
        if (dist < 1e-4) return false;

        Vec3d toNorm = to.multiply(1.0 / dist);
        double dot = clamp(forwardNorm.dotProduct(toNorm), -1.0, 1.0);
        double angleDeg = Math.toDegrees(Math.acos(dot));
        boolean isBehind = angleDeg > behindAngleDegrees; // treat very off-axis as behind

        if (isBehind) {
            return dist > behindMaxDistance;
        } else {
            return dist > frontMaxDistance;
        }
    }

    public static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
