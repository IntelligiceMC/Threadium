package com.itarqos.threadium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itarqos.threadium.util.ThreadiumLog;
import java.util.HashSet;
import java.util.Set;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

    public class ThreadiumConfig {
    public enum OverlayPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER
    }
    // Legacy/general flags (kept for compatibility)
    public boolean enableEntityCulling = true;          // master switch for entity culling behaviors
    public boolean enableChunkCulling = true;            // master switch for chunk-related culling
    public double entityCullingDistance = 96.0;          // unused by hidden-culling, kept for compatibility
    public boolean showCullingOverlay = true;            // when true, we render FPS overlay
    public OverlayPosition overlayPosition = OverlayPosition.TOP_LEFT; // FPS overlay anchor position
    public boolean enableDebugMode = false;              // when true, always show debug counters even without F3

    // New fine-grained controls
    public boolean enableEntityBehindCulling = true;     // cull entities behind the camera
    public boolean enableEntityVerticalBandCulling = true; // cull entities outside vertical band
    public boolean enableBlockEntityCulling = true;      // enable block entity hidden/vertical culling
    public boolean enableChunkVerticalBandCulling = true; // cull chunk sections outside vertical band
    public int verticalBandHalfHeight = 5;               // +/- blocks around camera Y

    public boolean enablePredictivePrefetch = true;      // allow slight forward-cone prefetching
    public boolean enableDynamicVerticalBand = true;     // widen/narrow Y band with speed/look
    public boolean lodThrottlingEnabled = true;          // throttle far entity/block-entity rendering
    public int frustumHysteresisTicks = 3;               // keep borderline boxes visible for N ticks
    public int sliceDebounceMillis = 200;                // min millis between section rerenders
    public boolean enablePartialMeshing = true;          // master toggle for sub-identifier partial meshing

    // Render task scheduler options
    public boolean enableRenderScheduler = true;         // enable smart rendering task scheduling
    public boolean verboseLogging = false;               // enable verbose debug logging

    // Visibility cache & gradual unhide
    public boolean enableVisibilityDeprioritization = true; // cache hidden sections and deprioritize after N hidden frames
    public int hiddenDeprioritizeFrames = 30;               // frames hidden before deprioritizing
    public int unhidePerTick = 8;                            // max sections to unhide (schedule) per tick when turning

    // Particle distance-based scaling
    public boolean particleDistanceScaling = true;           // enable distance-based particle thinning/quality
    public double particleNear = 16.0;                       // distance at which density=1.0
    public double particleFar = 64.0;                        // distance at which density reaches minimum
    public float particleMinDensity = 0.3f;                  // minimum fraction of particles rendered at/after far
    public float particleFarSizeScale = 0.8f;                // scale factor for particle size at/after far

    // Movement prediction
    public boolean enablePredictionEverywhere = true;        // apply movement-based prediction across rendering decisions
    public int predictionAheadTicks = 2;                      // how many ticks ahead to predict camera position

    // Frame-time QoS controller
    public int targetFps = 120;                               // target FPS for closed-loop adjustments (30..240)
    public double qosAggressiveness = 0.6;                    // 0..1 scaling of dynamic adjustments

    // Screen-space aware budgeter
    public boolean enableScreenSpaceBudgeter = true;          // prioritize high on-screen impact sections first
    public int sliceBudgetPerTick = 24;                        // max number of new dirty slices scheduled per tick

    // Turn-bias predictive prefetch
    public boolean enableTurnBiasPrefetch = true;             // widen forward cone during rapid turns
    public double turnBiasStrength = 0.6;                      // 0..1 strength of widening/extension

    // Micro-stutter guard
    public boolean enableMicroStutterGuard = true;            // freeze heavy work next frame after a long frame
    public int microStutterThresholdMs = 22;                   // frame time ms considered a long frame (e.g., >22ms ~ <45 FPS)

    // Particle tile budgeting (reservoir-like)
    public boolean enableParticleTileBudget = true;           // limit spawns per screen-space tile per tick
    public int particleTileBudget = 6;                         // allowed particles per tile per tick (scaled by QoS)
    public int particleTileSize = 32;                          // approximate world-space tile size used for bucketing

    // Particle filtering (GUI-driven)
    public boolean disableAllParticles = false;                // when true, block all particle spawns
    public Set<String> disabledParticleIds = new HashSet<>();  // registry ids of particles to block individually

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "threadium.json");

    public static ThreadiumConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                ThreadiumConfig cfg = GSON.fromJson(json, ThreadiumConfig.class);
                return cfg != null ? cfg : new ThreadiumConfig();
            }
        } catch (Exception e) {
            ThreadiumLog.error("Failed to read config: %s", e.getMessage());
        }
        return new ThreadiumConfig();
    }

    public static void save(ThreadiumConfig config) {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            String json = GSON.toJson(config);
            Files.writeString(CONFIG_PATH, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ThreadiumLog.error("Failed to save config: %s", e.getMessage());
        }
    }
}
