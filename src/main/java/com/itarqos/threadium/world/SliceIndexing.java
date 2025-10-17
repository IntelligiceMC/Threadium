package com.itarqos.threadium.world;

import net.minecraft.util.math.BlockPos;

/**
 * Utility for mapping world/block positions and chunk IDs to Threadium quadrant slice indices
 * and representative coordinates.
 */
public final class SliceIndexing {
    private SliceIndexing() {}

    /**
     * Map a block position to our 4-slice (quadrant) index inside its chunk.
     * 0..3 where bit0 is X quadrant and bit1 is Z quadrant.
     */
    public static int computeSliceIndex(BlockPos pos) {
        int lx = pos.getX() & 15; // 0..15
        int lz = pos.getZ() & 15; // 0..15
        int sx = lx >> 3; // 0 or 1
        int sz = lz >> 3; // 0 or 1
        return (sz << 1) | sx; // 0..3
    }

    /**
     * Get the world-space center (x,z) of a quadrant slice within a chunk.
     * Returns [cx, cz].
     */
    public static int[] quadrantCenterXZ(ChunkId id, int sliceIndex) {
        int baseX = (id.x() << 4);
        int baseZ = (id.z() << 4);
        int sx = (sliceIndex & 1);      // 0 or 1 (x quadrant)
        int sz = (sliceIndex >> 1) & 1; // 0 or 1 (z quadrant)
        int cx = baseX + (sx == 0 ? 4 : 12); // centers: 4 or 12
        int cz = baseZ + (sz == 0 ? 4 : 12);
        return new int[] { cx, cz };
    }
}
