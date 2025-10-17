package com.itarqos.threadium.client.particles;

import java.util.ArrayDeque;

/**
 * Simple object pool for PooledParticle to reduce GC churn.
 */
public final class ParticlePool {
    private final ArrayDeque<PooledParticle> free = new ArrayDeque<>();
    private final int maxSize;

    public ParticlePool(int maxSize) {
        this.maxSize = Math.max(32, maxSize);
    }

    public PooledParticle obtain() {
        PooledParticle p = free.pollFirst();
        return (p != null) ? p : new PooledParticle();
    }

    public void free(PooledParticle p) {
        if (p == null) return;
        if (free.size() < maxSize) {
            p.alive = false;
            free.addFirst(p);
        }
    }
}
