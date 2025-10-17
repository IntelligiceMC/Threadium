package com.itarqos.threadium.util;

public final class CullingStats {
    private CullingStats() {}

    private static int entitiesCulled = 0;
    private static int blockEntitiesCulled = 0;
    private static int slicesFlushed = 0;
    private static int slicesDebounced = 0;
    private static int slicesSkippedEmpty = 0;

    public static void incEntityCulled() {
        entitiesCulled++;
    }

    public static int getEntitiesCulled() {
        return entitiesCulled;
    }

    public static void incBlockEntityCulled() {
        blockEntitiesCulled++;
    }

    public static int getBlockEntitiesCulled() {
        return blockEntitiesCulled;
    }

    public static void incSliceFlushed() { slicesFlushed++; }
    public static int getSlicesFlushed() { return slicesFlushed; }

    public static void incSliceDebounced() { slicesDebounced++; }
    public static int getSlicesDebounced() { return slicesDebounced; }

    public static void incSliceSkippedEmpty() { slicesSkippedEmpty++; }
    public static int getSlicesSkippedEmpty() { return slicesSkippedEmpty; }

    public static void reset() {
        entitiesCulled = 0;
        blockEntitiesCulled = 0;
        slicesFlushed = 0;
        slicesDebounced = 0;
        slicesSkippedEmpty = 0;
    }
}
