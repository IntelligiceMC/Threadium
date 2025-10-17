package com.itarqos.threadium.world;

/**
 * Immutable key identifying a chunk-slice by chunk X/Z and quadrant slice index (0..3).
 */
public final class SectionKey {
    public final int x;
    public final int sliceIndex;
    public final int z;

    public SectionKey(int x, int sliceIndex, int z) {
        this.x = x;
        this.sliceIndex = sliceIndex;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SectionKey that = (SectionKey) o;
        return x == that.x && sliceIndex == that.sliceIndex && z == that.z;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(sliceIndex);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    @Override
    public String toString() {
        return "SectionKey{" +
                "x=" + x +
                ", sliceIndex=" + sliceIndex +
                ", z=" + z +
                '}';
    }
}
