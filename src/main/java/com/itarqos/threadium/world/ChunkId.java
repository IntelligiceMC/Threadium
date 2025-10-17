package com.itarqos.threadium.world;

import net.minecraft.util.math.ChunkPos;

public record ChunkId(int x, int z) {
    public static ChunkId of(ChunkPos pos) {
        return new ChunkId(pos.x, pos.z);
    }
    @Override
    public String toString() {
        return "ChunkId{" + x + "," + z + '}';
    }
}
