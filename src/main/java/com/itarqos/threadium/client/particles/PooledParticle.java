package com.itarqos.threadium.client.particles;

import net.minecraft.util.math.Vec3d;

/**
 * Lightweight particle state stored inside a chunk container.
 * Not a Minecraft Entity or Particle instance.
 */
public final class PooledParticle {
    public double x, y, z;
    public double vx, vy, vz;
    public float r = 1f, g = 1f, b = 1f, a = 1f;
    public float size = 0.1f;
    public int age;
    public int lifetime = 20;
    public int textureIndex = 0; // placeholder for sprite atlas index
    public boolean alive = false;

    public void init(Vec3d pos, Vec3d vel, int lifetime, float size, float r, float g, float b, float a, int textureIndex) {
        this.x = pos.x; this.y = pos.y; this.z = pos.z;
        this.vx = vel.x; this.vy = vel.y; this.vz = vel.z;
        this.lifetime = Math.max(1, lifetime);
        this.size = size;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.textureIndex = textureIndex;
        this.age = 0;
        this.alive = true;
    }

    public void tick() {
        if (!alive) return;
        // simple Euler integration; game gravity/drag can be applied by caller types if needed
        x += vx; y += vy; z += vz;
        age++;
        if (age >= lifetime) {
            alive = false;
        }
    }
}
