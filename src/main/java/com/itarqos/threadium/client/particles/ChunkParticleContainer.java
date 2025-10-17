package com.itarqos.threadium.client.particles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static net.minecraft.client.render.GameRenderer.*;

/**
 * Holds pooled particles for a single chunk and updates/renders them together.
 */
public final class ChunkParticleContainer {
    private final ChunkPos chunkPos;
    private final ParticlePool pool;
    private final List<PooledParticle> active = new ArrayList<>(256);
    // Reusable buffer per container to batch quads and submit a single draw call
    private final BufferBuilder buffer = new BufferBuilder(new BufferAllocator(1024), VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

    public ChunkParticleContainer(ChunkPos chunkPos, ParticlePool pool) {
        this.chunkPos = chunkPos;
        this.pool = pool;
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public void addParticle(Vec3d pos, Vec3d vel, int lifetime, float size,
                            float r, float g, float b, float a, int textureIndex) {
        PooledParticle p = pool.obtain();
        p.init(pos, vel, lifetime, size, r, g, b, a, textureIndex);
        active.add(p);
    }

    public void tick() {
        // Update all particles and return dead ones to pool
        Iterator<PooledParticle> it = active.iterator();
        while (it.hasNext()) {
            PooledParticle p = it.next();
            p.tick();
            if (!p.alive) {
                it.remove();
                pool.free(p);
            }
        }
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    /**
     * Placeholder for batched rendering. Currently a no-op to ensure safe compilation
     * without hard dependency on specific buffer builders. You can implement a proper
     * buffered draw by building a quad per particle into a shared BufferBuilder.
     */
    public void renderBatched(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getCameraEntity() == null || active.isEmpty()) return;

        // Camera data
        Vec3d camPos = mc.getCameraEntity().getCameraPosVec(tickDelta);
        Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();
        Vector3f right = new Vector3f(1f, 0f, 0f).rotate(camRot);
        Vector3f up = new Vector3f(0f, 1f, 0f).rotate(camRot);

        // BufferBuilder is already configured with DrawMode and VertexFormat via constructor in modern mappings.
        // No explicit begin() call is needed here.

        for (PooledParticle p : active) {
            if (!p.alive) continue;

            // Interpolate position for smoothness
            float px = (float) (p.x + p.vx * tickDelta - camPos.x);
            float py = (float) (p.y + p.vy * tickDelta - camPos.y);
            float pz = (float) (p.z + p.vz * tickDelta - camPos.z);
            float hs = p.size * 0.5f;

            // Quad corners (billboard facing camera):
            // v0 = center - right*hs - up*hs
            // v1 = center - right*hs + up*hs
            // v2 = center + right*hs + up*hs
            // v3 = center + right*hs - up*hs
            float rx = right.x * hs, ry = right.y * hs, rz = right.z * hs;
            float ux = up.x * hs, uy = up.y * hs, uz = up.z * hs;

            float r = p.r, g = p.g, b = p.b, a = p.a;

            // v0
            buffer.vertex(px - rx - ux, py - ry - uy, pz - rz - uz)
                  .color(r, g, b, a);
            // v1
            buffer.vertex(px - rx + ux, py - ry + uy, pz - rz + uz)
                  .color(r, g, b, a);
            // v2
            buffer.vertex(px + rx + ux, py + ry + uy, pz + rz + uz)
                  .color(r, g, b, a);
            // v3
            buffer.vertex(px + rx - ux, py + ry - uy, pz + rz - uz)
                  .color(r, g, b, a);
        }

        // Finish building; if nothing was emitted, bail out
        BuiltBuffer built = buffer.endNullable();
        if (built == null) return;

        // Submit a single draw call for this container
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR_LIGHTMAP);
        BufferRenderer.drawWithGlobalProgram(built);
    }

    public static ChunkPos posToChunk(double x, double z) {
        return new ChunkPos(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4);
    }
}
