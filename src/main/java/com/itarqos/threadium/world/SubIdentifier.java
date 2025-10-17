package com.itarqos.threadium.world;

import net.minecraft.util.math.BlockPos;

/**
 * Represents a vertical slice (sub-identifier) inside a chunk.
 * Only tracks non-air block counts for lightweight dirty checks.
 */
public class SubIdentifier {
    public static final int SLICE_HEIGHT = 16; // 16-block vertical slices

    private final ChunkId chunkId;
    private final int sliceY; // slice index (0..15 for 256 world height as example)
    private int nonAirCount;
    private boolean dirty;

    public SubIdentifier(ChunkId chunkId, int sliceY) {
        this.chunkId = chunkId;
        this.sliceY = sliceY;
    }

    public ChunkId chunkId() { return chunkId; }
    public int sliceY() { return sliceY; }

    public void onBlockSet(BlockPos pos, boolean becameNonAir) {
        if (becameNonAir) nonAirCount++; else nonAirCount = Math.max(0, nonAirCount - 1);
        dirty = true;
    }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public int nonAirCount() { return nonAirCount; }
}
