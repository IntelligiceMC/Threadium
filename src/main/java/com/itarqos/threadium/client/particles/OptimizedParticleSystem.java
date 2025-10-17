package com.itarqos.threadium.client.particles;

import com.itarqos.threadium.client.ThreadiumClient;
import com.itarqos.threadium.util.FrameBudgetController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-only particle system that groups sub-particles per chunk for
 * batched ticking and rendering. No server-side entities are created.
 */
public final class OptimizedParticleSystem {
    private static final OptimizedParticleSystem INSTANCE = new OptimizedParticleSystem();

    public static OptimizedParticleSystem get() { return INSTANCE; }

    private final Map<Long, ChunkParticleContainer> containers = new HashMap<>();
    // Per-tick tile counts for budgeting
    private final Map<Long, Integer> tileCounts = new HashMap<>();
    private final ParticlePool pool = new ParticlePool(4096);

    private OptimizedParticleSystem() {}

    private static long key(ChunkPos pos) {
        return (((long) pos.x) << 32) ^ (pos.z & 0xffffffffL);
    }

    private ChunkParticleContainer getOrCreate(ChunkPos pos) {
        return containers.computeIfAbsent(key(pos), k -> new ChunkParticleContainer(pos, pool));
    }

    public void spawn(Vec3d pos, Vec3d vel, int lifetime, float size,
                      float r, float g, float b, float a, int textureIndex) {
        // Micro-stutter guard: drop optional work if last frame was long
        if (ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enableMicroStutterGuard && FrameBudgetController.get().wasLongFrame()) {
            return;
        }

        // Tile budget check (world-space tiling)
        if (ThreadiumClient.CONFIG == null || ThreadiumClient.CONFIG.enableParticleTileBudget) {
            int tileSize = Math.max(4, ThreadiumClient.CONFIG != null ? ThreadiumClient.CONFIG.particleTileSize : 32);
            int tx = (int)Math.floor(pos.x / tileSize);
            int tz = (int)Math.floor(pos.z / tileSize);
            long tkey = (((long)tx) << 32) ^ (tz & 0xffffffffL);
            int baseBudget = ThreadiumClient.CONFIG != null ? Math.max(1, ThreadiumClient.CONFIG.particleTileBudget) : 6;
            int budget = (int)Math.max(1, Math.floor(baseBudget * FrameBudgetController.get().getParticleBudgetScale()));
            int used = tileCounts.getOrDefault(tkey, 0);
            if (used >= budget) {
                return;
            }
            tileCounts.put(tkey, used + 1);
        }
        // Distance-based thinning & quality scaling
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.particleDistanceScaling && mc.getCameraEntity() != null) {
            Vec3d cam = mc.getCameraEntity().getPos();
            if (ThreadiumClient.CONFIG.enablePredictionEverywhere) {
                cam = com.itarqos.threadium.util.MovementPredictor.get().getPredictedCamPos(Math.max(0, ThreadiumClient.CONFIG.predictionAheadTicks));
            }
            double d = cam.distanceTo(pos);
            double near = Math.max(0.0, ThreadiumClient.CONFIG.particleNear);
            double far = Math.max(near + 1.0, ThreadiumClient.CONFIG.particleFar);
            double t = (d - near) / (far - near);
            if (t < 0) t = 0; else if (t > 1) t = 1;
            float minDensity = Math.max(0.0f, Math.min(1.0f, ThreadiumClient.CONFIG.particleMinDensity));
            float density = (float)(1.0 - t) + (float)t * minDensity; // lerp(1, minDensity, t)
            // Randomly drop spawns beyond density
            if (ThreadLocalRandom.current().nextFloat() > density) {
                return;
            }
            // Scale particle size toward far size scale
            float sizeScaleFar = Math.max(0.1f, ThreadiumClient.CONFIG.particleFarSizeScale);
            float scale = (float)(1.0 - t) + (float)t * sizeScaleFar; // lerp(1, farScale, t)
            size *= scale;
        }
        ChunkPos cp = ChunkParticleContainer.posToChunk(pos.x, pos.z);
        getOrCreate(cp).addParticle(pos, vel, lifetime, size, r, g, b, a, textureIndex);
    }

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        ClientWorld world = mc.world;
        // Update all and drop empty/unloaded
        Iterator<Map.Entry<Long, ChunkParticleContainer>> it = containers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ChunkParticleContainer> e = it.next();
            ChunkParticleContainer c = e.getValue();
            // unload if world is null or chunk not loaded
            if (world == null || !world.getChunkManager().isChunkLoaded(c.getChunkPos().x, c.getChunkPos().z)) {
                it.remove();
                continue;
            }
            c.tick();
            if (c.isEmpty()) {
                it.remove();
            }
        }
        // Reset tile counts each tick
        tileCounts.clear();
    }

    public void render(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        for (ChunkParticleContainer c : containers.values()) {
            c.renderBatched(tickDelta);
        }
    }

    public void clearAll() {
        containers.clear();
    }
}
