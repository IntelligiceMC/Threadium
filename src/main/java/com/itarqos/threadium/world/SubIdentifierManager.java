package com.itarqos.threadium.world;

import com.itarqos.threadium.util.CullingUtil;
import com.itarqos.threadium.util.CullingStats;
import com.itarqos.threadium.util.MovementPredictor;
import com.itarqos.threadium.util.FrameBudgetController;
import com.itarqos.threadium.client.ThreadiumClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import com.itarqos.threadium.mixin.render.WorldRendererAccessor;

/**
 * Tracks dirty vertical slices (subidentifiers) per chunk and schedules rerenders
 * only when the slice becomes visible to the player (just-in-time meshing).
 */
public final class SubIdentifierManager {
    private static final SubIdentifierManager INSTANCE = new SubIdentifierManager();

    public static SubIdentifierManager get() { return INSTANCE; }

    private final Map<ChunkId, BitSet> dirtySlices = new HashMap<>(); // 16 slices per chunk (0..15)
    private final Map<ChunkId, int[]> sliceNonAirCounts = new HashMap<>(); // 16-length arrays
    private final Map<SectionKey, Long> lastScheduledMs = new HashMap<>();

    // Visibility cache & gradual unhide state
    private long frameCounter = 0L;
    private final Map<SectionKey, Long> lastVisibleFrame = new HashMap<>();
    private final Deque<BlockPos> pendingUnhide = new ArrayDeque<>();

    private SubIdentifierManager() {}

    public void markBlockChanged(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkId id = new ChunkId(cp.x, cp.z);
        int slice = Math.max(0, Math.min(15, pos.getY() >> 4));
        dirtySlices.computeIfAbsent(id, k -> new BitSet(16)).set(slice);
    }

    // Called from mixin when old/new states are known
    public void onBlockStateChanged(BlockPos pos, boolean oldWasAir, boolean newIsAir) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkId id = new ChunkId(cp.x, cp.z);
        int slice = Math.max(0, Math.min(15, pos.getY() >> 4));
        dirtySlices.computeIfAbsent(id, k -> new BitSet(16)).set(slice);
        int[] counts = sliceNonAirCounts.computeIfAbsent(id, k -> new int[16]);
        if (oldWasAir && !newIsAir) {
            counts[slice] = Math.max(0, counts[slice] + 1);
        } else if (!oldWasAir && newIsAir) {
            counts[slice] = Math.max(0, counts[slice] - 1);
        }
    }

    private boolean isSliceEmpty(ChunkId id, int slice) {
        int[] counts = sliceNonAirCounts.get(id);
        if (counts == null) return false; // unknown -> assume non-empty to be safe
        return counts[slice] <= 0;
    }

    private boolean debounce(ChunkId id, int sliceY) {
        long now = System.currentTimeMillis();
        int debounceMs = ThreadiumClient.CONFIG != null ? Math.max(0, ThreadiumClient.CONFIG.sliceDebounceMillis) : 200;
        SectionKey key = new SectionKey(id.x(), sliceY, id.z());
        Long last = lastScheduledMs.get(key);
        if (last != null && (now - last) < debounceMs) {
            return true; // should debounce
        }
        lastScheduledMs.put(key, now);
        return false;
    }

    /**
     * Mark a section as visible on this frame (called when scheduleSectionRender is not cancelled).
     */
    public void markSectionVisible(BlockPos pos) {
        int slice = Math.max(0, Math.min(15, pos.getY() >> 4));
        ChunkPos cp = new ChunkPos(pos);
        SectionKey key = new SectionKey(cp.x, slice, cp.z);
        lastVisibleFrame.put(key, frameCounter);
    }

    public void flushVisible() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getCameraEntity() == null || mc.world == null) return;
        WorldRenderer wr = mc.worldRenderer;
        if (wr == null) return;

        // Advance frame counter per client tick
        frameCounter++;

        // If partial meshing is disabled, schedule all dirty slices immediately
        if (ThreadiumClient.CONFIG != null && !ThreadiumClient.CONFIG.enablePartialMeshing) {
            Iterator<Map.Entry<ChunkId, BitSet>> itAll = dirtySlices.entrySet().iterator();
            while (itAll.hasNext()) {
                Map.Entry<ChunkId, BitSet> e = itAll.next();
                ChunkId id = e.getKey();
                BitSet bits = e.getValue();
                if (bits.isEmpty()) { itAll.remove(); continue; }
                for (int slice = bits.nextSetBit(0); slice >= 0; slice = bits.nextSetBit(slice + 1)) {
                    int yCenter = (slice << 4) + 8;
                    int xBlock = (id.x() << 4) + 8;
                    int zBlock = (id.z() << 4) + 8;
                    BlockPos pos = new BlockPos(xBlock, yCenter, zBlock);
                    ((WorldRendererAccessor) wr).threadium$invokeScheduleSectionRender(pos, false);
                    CullingStats.incSliceFlushed();
                }
                itAll.remove();
            }
            return;
        }

        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d camPos = mc.getCameraEntity().getCameraPosVec(tickDelta);
        if (ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enablePredictionEverywhere) {
            camPos = MovementPredictor.get().getPredictedCamPos(Math.max(0, ThreadiumClient.CONFIG.predictionAheadTicks));
        }
        // Use smoothed forward from predictor for stability
        Vec3d forward = MovementPredictor.get().getSmoothedForward();
        double speed = MovementPredictor.get().getSmoothedSpeed();

        // Frame-time QoS multipliers
        double farCutoffMult = FrameBudgetController.get().getFarCutoffMultiplier();
        int baseSliceBudget = ThreadiumClient.CONFIG != null ? Math.max(0, ThreadiumClient.CONFIG.sliceBudgetPerTick) : 24;
        int sliceBudget = (int)Math.floor(baseSliceBudget * FrameBudgetController.get().getUnhideMultiplier());
        if (FrameBudgetController.get().wasLongFrame() && ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enableMicroStutterGuard) {
            sliceBudget = 0; // freeze new work after a long frame
        }

        // Turn-bias prefetch widening
        double turnBiasStrength = (ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enableTurnBiasPrefetch)
                ? Math.max(0.0, Math.min(1.0, ThreadiumClient.CONFIG.turnBiasStrength))
                : 0.0;
        double angAccel = MovementPredictor.get().getAngularAcceleration();

        // Dynamic thresholds: move more -> look farther ahead, slightly less behind
        double baseFront = 64.0;
        double baseBehind = 32.0;
        double baseFar = 98.0;
        double frontMaxDistance = baseFront + Math.min(48.0, speed * 96.0);   // up to +48 blocks ahead
        double behindMaxDistance = Math.max(12.0, baseBehind - Math.min(16.0, speed * 24.0)); // down to 12
        double farCutoff = baseFar + Math.min(32.0, speed * 48.0);            // prefetch a bit farther when fast
        // Apply QoS scaling
        frontMaxDistance *= farCutoffMult;
        behindMaxDistance *= farCutoffMult;
        farCutoff *= farCutoffMult;
        // Apply turn-bias: widen cone and slightly extend far cutoff during rapid turns
        double turnWidenDeg = 0.0;
        if (turnBiasStrength > 0.0) {
            double turnMag = Math.min(1.0, Math.abs(angAccel) * 6.0); // rough mapping
            turnWidenDeg = 30.0 * turnBiasStrength * turnMag; // up to +30° widening
            farCutoff *= (1.0 + 0.15 * turnBiasStrength * turnMag);
        }

        // Collect eligible candidates for prioritized scheduling
        class Cand { final BlockPos pos; final double score; final ChunkId id; final int slice; Cand(BlockPos p,double s,ChunkId i,int sl){pos=p;score=s;id=i;slice=sl;} }
        java.util.ArrayList<Cand> candidates = new java.util.ArrayList<>();

        Iterator<Map.Entry<ChunkId, BitSet>> it = dirtySlices.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkId, BitSet> e = it.next();
            ChunkId id = e.getKey();
            BitSet bits = e.getValue();
            if (bits.isEmpty()) { it.remove(); continue; }

            // For each dirty slice, pick the center of the slice inside the chunk to test visibility
            for (int slice = bits.nextSetBit(0); slice >= 0; slice = bits.nextSetBit(slice + 1)) {
                int yMin = slice << 4;
                int yCenter = yMin + 8;
                int xBlock = (id.x() << 4) + 8;
                int zBlock = (id.z() << 4) + 8;
                Vec3d target = new Vec3d(xBlock + 0.5, yCenter + 0.5, zBlock + 0.5);

                boolean culledBehind = CullingUtil.shouldCullByAngleAndDistance(
                        camPos, forward, target,
                        frontMaxDistance,
                        behindMaxDistance,
                        125.0
                );
                double dist = target.distanceTo(camPos);
                boolean tooFar = dist > farCutoff; // general far cutoff (dynamic)

                // Light prefetch in a forward cone: widen when turning
                boolean inForwardCone = false;
                {
                    Vec3d to = target.subtract(camPos);
                    double len = to.length();
                    if (len > 1e-4) {
                        Vec3d toN = to.multiply(1.0 / len);
                        double dot = CullingUtil.clamp(forward.dotProduct(toN), -1.0, 1.0);
                        double ang = Math.toDegrees(Math.acos(dot));
                        inForwardCone = ang < (35.0 + turnWidenDeg);
                    }
                }

                boolean allowPrefetch = ThreadiumClient.CONFIG == null || ThreadiumClient.CONFIG.enablePredictivePrefetch;

                if (!(culledBehind || tooFar) || (allowPrefetch && inForwardCone && dist < farCutoff * 1.15)) {
                    // Skip empty slices when we know they're empty
                    if (isSliceEmpty(id, slice)) {
                        CullingStats.incSliceSkippedEmpty();
                        bits.clear(slice);
                        continue;
                    }
                    // Debounce to avoid thrash
                    if (debounce(id, slice)) {
                        CullingStats.incSliceDebounced();
                        continue;
                    }
                    // Visible: either gradual-unhide queue or candidate list
                    BlockPos pos = new BlockPos(xBlock, yCenter, zBlock);
                    boolean useGradual = ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enableVisibilityDeprioritization;
                    int threshold = ThreadiumClient.CONFIG != null ? Math.max(0, ThreadiumClient.CONFIG.hiddenDeprioritizeFrames) : 30;
                    SectionKey key = new SectionKey(id.x(), slice, id.z());
                    long lastSeen = lastVisibleFrame.getOrDefault(key, frameCounter);
                    long hiddenFor = frameCounter - lastSeen;
                    if (useGradual && hiddenFor >= threshold) {
                        pendingUnhide.addLast(pos);
                        bits.clear(slice);
                    } else {
                        // Screen-impact heuristic score: prefer closer and more forward-aligned slices
                        Vec3d to = new Vec3d(xBlock + 0.5, yCenter + 0.5, zBlock + 0.5).subtract(camPos);
                        double len = to.length();
                        double invDist = 1.0 / (1.0 + len);
                        Vec3d toN = len > 1e-4 ? to.multiply(1.0 / len) : new Vec3d(0,0,0);
                        double dot = CullingUtil.clamp(forward.dotProduct(toN), -1.0, 1.0);
                        double forwardFavor = Math.max(0.0, dot); // behind gets 0
                        double yFavor = Math.max(0.0, 1.0 - (Math.abs((yCenter + 0.5) - camPos.y) / 24.0)); // prefer similar Y
                        double score = invDist * (0.6 + 0.3 * forwardFavor + 0.1 * yFavor);
                        candidates.add(new Cand(pos, score, id, slice));
                    }
                }
            }
        }

        // Prioritize and schedule within budget
        if (!candidates.isEmpty() && sliceBudget > 0) {
            boolean useScreenSpace = ThreadiumClient.CONFIG == null || ThreadiumClient.CONFIG.enableScreenSpaceBudgeter;
            if (useScreenSpace) {
                candidates.sort((a,b) -> Double.compare(b.score, a.score));
            }
            int scheduled = 0;
            for (int i = 0; i < candidates.size() && scheduled < sliceBudget; i++) {
                Cand c = candidates.get(i);
                ((WorldRendererAccessor) wr).threadium$invokeScheduleSectionRender(c.pos, false);
                CullingStats.incSliceFlushed();
                BitSet bits = dirtySlices.get(c.id);
                if (bits != null) {
                    bits.clear(c.slice);
                }
                scheduled++;
            }
        }
        // Prune emptied entries
        dirtySlices.entrySet().removeIf(en -> en.getValue() == null || en.getValue().isEmpty());

        // Drain pending unhide queue up to configured budget per tick
        if (ThreadiumClient.CONFIG == null || !ThreadiumClient.CONFIG.enableVisibilityDeprioritization) {
            pendingUnhide.clear();
        } else {
            int budget = Math.max(0, ThreadiumClient.CONFIG.unhidePerTick);
            // Apply QoS scaling and micro-stutter guard
            budget = (int)Math.floor(budget * FrameBudgetController.get().getUnhideMultiplier());
            if (FrameBudgetController.get().wasLongFrame() && ThreadiumClient.CONFIG.enableMicroStutterGuard) {
                budget = 0;
            }
            int processed = 0;
            while (processed < budget && !pendingUnhide.isEmpty()) {
                BlockPos pos = pendingUnhide.pollFirst();
                ((WorldRendererAccessor) wr).threadium$invokeScheduleSectionRender(pos, false);
                CullingStats.incSliceFlushed();
                processed++;
            }
        }
    }

    /**
     * Purge all state for a chunk when it unloads from the client world.
     */
    public void onChunkUnload(ChunkPos pos) {
        ChunkId id = ChunkId.of(pos);
        dirtySlices.remove(id);
        sliceNonAirCounts.remove(id);
        // prune any debounced keys that belong to this chunk
        lastScheduledMs.entrySet().removeIf(entry -> {
            SectionKey k = entry.getKey();
            return k.x == id.x() && k.z == id.z();
        });
        // clear visibility cache entries for this chunk
        lastVisibleFrame.entrySet().removeIf(entry -> entry.getKey().x == id.x() && entry.getKey().z == id.z());
        pendingUnhide.removeIf(p -> {
            ChunkPos cp = new ChunkPos(p);
            return cp.x == id.x() && cp.z == id.z();
        });
    }

    /**
     * Clear all state when the renderer/world invalidates (e.g., resource reload, F3+A, dimension change).
     */
    public void onWorldReset() {
        dirtySlices.clear();
        sliceNonAirCounts.clear();
        lastScheduledMs.clear();
        lastVisibleFrame.clear();
        pendingUnhide.clear();
        frameCounter = 0L;
    }

    /**
     * Simple immutable key for identifying a chunk slice by chunk X/Z and slice Y index (0..15).
     */
    private static final class SectionKey {
        private final int x;
        private final int sliceY;
        private final int z;

        private SectionKey(int x, int sliceY, int z) {
            this.x = x;
            this.sliceY = sliceY;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SectionKey that = (SectionKey) o;
            return x == that.x && sliceY == that.sliceY && z == that.z;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(sliceY);
            result = 31 * result + Integer.hashCode(z);
            return result;
        }

        @Override
        public String toString() {
            return "SectionKey{" +
                    "x=" + x +
                    ", sliceY=" + sliceY +
                    ", z=" + z +
                    '}';
        }
    }
}
