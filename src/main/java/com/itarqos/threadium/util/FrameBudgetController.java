package com.itarqos.threadium.util;

import com.itarqos.threadium.client.ThreadiumClient;

/**
 * Tracks frame time and provides dynamic budgets/LOD knobs to keep FPS near a target.
 * Uses an EMA of frame time and simple heuristics for aggressiveness.
 */
public final class FrameBudgetController {
    private static final FrameBudgetController INSTANCE = new FrameBudgetController();
    public static FrameBudgetController get() { return INSTANCE; }

    private long lastFrameStartNs = 0L;
    private double emaFrameMs = 7.0; // initialize around ~144 FPS
    private static final double ALPHA = 0.2; // smoothing for frame time

    // Snapshot each frame
    private double lastFrameMs = 7.0;
    private boolean longFrame = false;

    private FrameBudgetController() {}

    public void beginFrame() {
        long now = System.nanoTime();
        if (lastFrameStartNs != 0L) {
            long dtNs = now - lastFrameStartNs;
            lastFrameMs = dtNs / 1_000_000.0;
            emaFrameMs = emaFrameMs * (1.0 - ALPHA) + lastFrameMs * ALPHA;
            updateLongFrameFlag();
        }
        lastFrameStartNs = now;
    }

    private void updateLongFrameFlag() {
        if (ThreadiumClient.CONFIG == null) { longFrame = false; return; }
        if (!ThreadiumClient.CONFIG.enableMicroStutterGuard) { longFrame = false; return; }
        int threshold = Math.max(10, ThreadiumClient.CONFIG.microStutterThresholdMs);
        longFrame = lastFrameMs > threshold;
    }

    public double getEmaFrameMs() { return emaFrameMs; }
    public double getLastFrameMs() { return lastFrameMs; }
    public boolean wasLongFrame() { return longFrame; }

    // 0..1 aggressiveness, 0=off
    private double aggr() {
        if (ThreadiumClient.CONFIG == null) return 0.0;
        double a = Math.max(0.0, Math.min(1.0, ThreadiumClient.CONFIG.qosAggressiveness));
        return a;
    }

    private double targetMs() {
        if (ThreadiumClient.CONFIG == null) return 8.0; // ~125 FPS default
        int fps = Math.max(30, Math.min(240, ThreadiumClient.CONFIG.targetFps));
        return 1000.0 / fps;
        
    }

    /**
     * Multiplier for how many sections to unhide/schedule per tick. 1.0 = baseline.
     * Reduces when frame time is above target.
     */
    public double getUnhideMultiplier() {
        double t = targetMs();
        double e = (t - emaFrameMs) / t; // negative when over budget
        // Map error to multiplier in [0.5, 1.5]
        double base = 1.0 + 0.8 * aggr() * e;
        if (base < 0.5) base = 0.5;
        if (base > 1.5) base = 1.5;
        return base;
    }

    /**
     * Multiplier for far cutoff distances to shrink/grow world work.
     */
    public double getFarCutoffMultiplier() {
        double t = targetMs();
        double e = (t - emaFrameMs) / t;
        double base = 1.0 + 0.5 * aggr() * e;
        if (base < 0.75) base = 0.75;
        if (base > 1.25) base = 1.25;
        return base;
    }

    /**
     * Integer LOD level: 0=normal, 1=aggressive, 2=very aggressive.
     */
    public int getLodLevel() {
        double t = targetMs();
        if (emaFrameMs < t * 1.05) return 0;
        if (emaFrameMs < t * 1.25) return 1;
        return 2;
    }

    /**
     * Scale for particle spawn budget [0.25, 1.5].
     */
    public double getParticleBudgetScale() {
        double t = targetMs();
        double e = (t - emaFrameMs) / t;
        double base = 1.0 + 1.0 * aggr() * e;
        if (base < 0.25) base = 0.25;
        if (base > 1.5) base = 1.5;
        return base;
    }
}
