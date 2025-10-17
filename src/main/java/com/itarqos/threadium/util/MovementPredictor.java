package com.itarqos.threadium.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Learns recent player movement to predict forward direction and speed.
 * Provides smoothed forward vector and scalar speed for culling biasing.
 */
public final class MovementPredictor {
    private static final MovementPredictor INSTANCE = new MovementPredictor();

    public static MovementPredictor get() { return INSTANCE; }

    // Exponential moving average for velocity and forward
    private Vec3d emaVelocity = Vec3d.ZERO;
    private Vec3d emaForward = new Vec3d(0, 0, 1);
    private double emaSpeed = 0.0;
    // Last known camera position (for simple future prediction)
    private Vec3d lastCamPos = Vec3d.ZERO;
    // Angular motion
    private double emaAngularSpeed = 0.0;   // radians per tick (approx)
    private double emaAngularAccel = 0.0;   // radians per tick^2 (approx)
    private Vec3d lastForward = new Vec3d(0, 0, 1);

    // 0..1 smoothing factors
    private static final double ALPHA_VEL = 0.25;   // velocity smoothing
    private static final double ALPHA_DIR = 0.20;   // forward smoothing
    private static final double ALPHA_SPD = 0.20;   // speed smoothing
    private static final double ALPHA_ANG = 0.25;   // angular smoothing

    private MovementPredictor() {}

    public void update(double tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getCameraEntity() == null) return;
        Vec3d camPosPrev = mc.getCameraEntity().prevX != 0 || mc.getCameraEntity().prevY != 0 || mc.getCameraEntity().prevZ != 0
                ? new Vec3d(mc.getCameraEntity().prevX, mc.getCameraEntity().prevY, mc.getCameraEntity().prevZ)
                : mc.getCameraEntity().getPos();
        Vec3d camPos = mc.getCameraEntity().getCameraPosVec((float) tickDelta);
        Vec3d vel = camPos.subtract(camPosPrev);
        lastCamPos = camPos;

        // EMA for velocity
        emaVelocity = emaVelocity.multiply(1.0 - ALPHA_VEL).add(vel.multiply(ALPHA_VEL));

        // EMA for forward/look vector
        Vec3d forward = mc.getCameraEntity().getRotationVec((float) tickDelta).normalize();
        emaForward = lerpDir(emaForward, forward, ALPHA_DIR);

        // Angular velocity from change in forward vector
        double dot = clamp(emaForward.dotProduct(lastForward), -1.0, 1.0);
        double deltaAngle = Math.acos(dot); // radians
        double angVel = deltaAngle; // approx per tick
        double prevAng = emaAngularSpeed;
        emaAngularSpeed = emaAngularSpeed * (1.0 - ALPHA_ANG) + angVel * ALPHA_ANG;
        // Angular acceleration as change in smoothed angular speed
        double angAcc = emaAngularSpeed - prevAng;
        emaAngularAccel = emaAngularAccel * (1.0 - ALPHA_ANG) + angAcc * ALPHA_ANG;
        lastForward = emaForward;

        // EMA for speed (blocks per tick approximation)
        double spd = vel.length();
        emaSpeed = emaSpeed * (1.0 - ALPHA_SPD) + spd * ALPHA_SPD;
    }

    private static Vec3d lerpDir(Vec3d a, Vec3d b, double t) {
        Vec3d v = a.multiply(1.0 - t).add(b.multiply(t));
        double len = v.length();
        if (len < 1e-6) return a;
        return v.multiply(1.0 / len);
    }

    public Vec3d getSmoothedForward() { return emaForward; }

    public double getSmoothedSpeed() { return emaSpeed; }

    public Vec3d getSmoothedVelocity() { return emaVelocity; }

    public double getAngularSpeed() { return emaAngularSpeed; }

    public double getAngularAcceleration() { return emaAngularAccel; }

    /**
     * Predict a future camera position assuming constant recent velocity for N ticks ahead.
     * Example: ticksAhead=2 predicts roughly two ticks into the future.
     */
    public Vec3d getPredictedCamPos(int ticksAhead) {
        if (ticksAhead <= 0) return lastCamPos;
        return lastCamPos.add(emaVelocity.multiply(ticksAhead));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
