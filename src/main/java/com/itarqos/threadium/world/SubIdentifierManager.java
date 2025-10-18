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
 * Tracks dirty quadrant slices (subidentifiers) per chunk and schedules rerenders
 * only when the slice becomes visible to the player (just-in-time meshing).
 *
 * New slicing scheme:
 * - 4 slices per chunk (2x2 in XZ), each slice is 8x8 blocks in XZ spanning the full vertical range.
 * - Slice index mapping (local X/Z inside chunk, 0..15):
 *   sx = (x & 15) >> 3, sz = (z & 15) >> 3, sliceIndex = (sz << 1) | sx  // 0..3
 */
public final class SubIdentifierManager {
    private static final SubIdentifierManager INSTANCE = new SubIdentifierManager();

    public static SubIdentifierManager get() { return INSTANCE; }

    private final Map<ChunkId, BitSet> dirtySlices = new HashMap<>(); // 4 slices per chunk (0..3)
    // For each chunk, maintain per-slice (0..3) set of pending section-origins (as long) that must be scheduled
    private final Map<ChunkId, java.util.HashSet<Long>[]> slicePendingSections = new HashMap<>();
    private final Map<SectionKey, Long> lastScheduledMs = new HashMap<>();

    // Visibility cache & gradual unhide state
    private long frameCounter = 0L;
    private final Map<SectionKey, Long> lastVisibleFrame = new HashMap<>();
    // Queue of section origins to gradually unhide/schedule (BlockPos aligned to 16x16x16 section origins)
    private final Deque<BlockPos> pendingUnhide = new ArrayDeque<>();

    private SubIdentifierManager() {}

    public void markBlockChanged(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkId id = new ChunkId(cp.x, cp.z);
        int slice = SliceIndexing.computeSliceIndex(pos);
        dirtySlices.computeIfAbsent(id, k -> new BitSet(4)).set(slice);
        java.util.HashSet<Long>[] arr = slicePendingSections.computeIfAbsent(id, k -> {
            @SuppressWarnings("unchecked")
            java.util.HashSet<Long>[] a = new java.util.HashSet[4];
            return a;
        });
        if (arr[slice] == null) arr[slice] = new java.util.HashSet<>();
        long origin = BlockPos.asLong((pos.getX() >> 4) << 4, (pos.getY() >> 4) << 4, (pos.getZ() >> 4) << 4);
        arr[slice].add(origin);
    }

    // Called from mixin when old/new states are known
    public void onBlockStateChanged(BlockPos pos, boolean oldWasAir, boolean newIsAir) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkId id = new ChunkId(cp.x, cp.z);
        int slice = SliceIndexing.computeSliceIndex(pos);
        dirtySlices.computeIfAbsent(id, k -> new BitSet(4)).set(slice);
        // Always track section-origin as pending; even if a block was placed then removed quickly,
        // we still need to rebuild the section once to reflect changes.
        java.util.HashSet<Long>[] arr = slicePendingSections.computeIfAbsent(id, k -> {
            @SuppressWarnings("unchecked")
            java.util.HashSet<Long>[] a = new java.util.HashSet[4];
            return a;
        });
        if (arr[slice] == null) arr[slice] = new java.util.HashSet<>();
        long origin = BlockPos.asLong((pos.getX() >> 4) << 4, (pos.getY() >> 4) << 4, (pos.getZ() >> 4) << 4);
        arr[slice].add(origin);
    }

    private boolean isSliceEmpty(ChunkId id, int slice) {
        java.util.HashSet<Long>[] arr = slicePendingSections.get(id);
        if (arr == null || arr[slice] == null) return true;
        return arr[slice].isEmpty();
    }

    private boolean debounce(ChunkId id, int sliceIndex) {
        long now = System.currentTimeMillis();
        int debounceMs = ThreadiumClient.CONFIG != null ? Math.max(0, ThreadiumClient.CONFIG.sliceDebounceMillis) : 200;
        SectionKey key = new SectionKey(id.x(), sliceIndex, id.z());
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
        // scheduleSectionRender is per 16x16 section (covers all quadrants in XZ).
        // Mark all quadrant slices of this chunk as visible this frame.
        ChunkPos cp = new ChunkPos(pos);
        for (int s = 0; s < 4; s++) {
            SectionKey key = new SectionKey(cp.x, s, cp.z);
            lastVisibleFrame.put(key, frameCounter);
        }
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
                java.util.HashSet<Long>[] arr = slicePendingSections.get(id);
                for (int slice = bits.nextSetBit(0); slice >= 0; slice = bits.nextSetBit(slice + 1)) {
                    if (arr != null && arr[slice] != null) {
                        for (long lp : arr[slice]) {
                            BlockPos origin = BlockPos.fromLong(lp);
                            ((WorldRendererAccessor) wr).threadium$invokeScheduleSectionRender(origin, false);
                            CullingStats.incSliceFlushed();
                        }
                        // clear per-slice pending after scheduling all
                        arr[slice].clear();
                    }
                }
                // clear entire chunk entry after flushing
                slicePendingSections.remove(id);
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

            // For each dirty slice, pick the center of the corresponding XZ quadrant to test visibility
            for (int slice = bits.nextSetBit(0); slice >= 0; slice = bits.nextSetBit(slice + 1)) {
                // quadrant centers
                int[] cxz = SliceIndexing.quadrantCenterXZ(id, slice);
                // choose Y near camera for better angle/distance heuristic
                int yCenter = (int)Math.round(camPos.y);
                Vec3d target = new Vec3d(cxz[0] + 0.5, yCenter + 0.5, cxz[1] + 0.5);

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
                    // Debounce to avoid thrash (per-slice index 0..3)
                    if (debounce(id, slice)) {
                        CullingStats.incSliceDebounced();
                        continue;
                    }
                    // Visible: either gradual-unhide queue or candidate list
                    BlockPos pos = new BlockPos(cxz[0], yCenter, cxz[1]);
                    boolean useGradual = ThreadiumClient.CONFIG != null && ThreadiumClient.CONFIG.enableVisibilityDeprioritization;
                    int threshold = ThreadiumClient.CONFIG != null ? Math.max(0, ThreadiumClient.CONFIG.hiddenDeprioritizeFrames) : 30;
                    SectionKey key = new SectionKey(id.x(), slice, id.z());
                    long lastSeen = lastVisibleFrame.getOrDefault(key, frameCounter);
                    long hiddenFor = frameCounter - lastSeen;
                    if (useGradual && hiddenFor >= threshold) {
                        // Convert slice's valid blocks into unique section origins and queue them
                        java.util.HashSet<Long>[] arr = slicePendingSections.get(id);
                        if (arr != null && arr[slice] != null) {
                            for (long lp : arr[slice]) {
                                BlockPos origin = BlockPos.fromLong(lp);
                                pendingUnhide.addLast(origin);
                            }
                        }
                        bits.clear(slice);
                        // we've enqueued work; clear tracked set for this slice
                        if (arr != null && arr[slice] != null) arr[slice].clear();
                    } else {
                        // Screen-impact heuristic score: prefer closer and more forward-aligned slices
                        Vec3d to = new Vec3d(cxz[0] + 0.5, yCenter + 0.5, cxz[1] + 0.5).subtract(camPos);
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
                // Convert this slice's tracked valid blocks into unique section origins
                java.util.HashSet<Long>[] arr = slicePendingSections.get(c.id);
                if (arr != null && arr[c.slice] != null) {
                    java.util.Iterator<Long> itL = arr[c.slice].iterator();
                    while (itL.hasNext() && scheduled < sliceBudget) {
                        long lp = itL.next();
                        BlockPos origin = BlockPos.fromLong(lp);
                        ((WorldRendererAccessor) wr).threadium$invokeScheduleSectionRender(origin, false);
                        CullingStats.incSliceFlushed();
                        scheduled++;
                        // remove scheduled origin from pending set
                        itL.remove();
                    }
                    // Clear this slice's dirty bit if nothing pending remains
                    if (arr[c.slice].isEmpty()) {
                        BitSet bits = dirtySlices.get(c.id);
                        if (bits != null) {
                            bits.clear(c.slice);
                        }
                    }
                }
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
        slicePendingSections.remove(id);
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
        slicePendingSections.clear();
        lastScheduledMs.clear();
        lastVisibleFrame.clear();
        pendingUnhide.clear();
        frameCounter = 0L;
    }

    // SectionKey is now a top-level class in this package.
    // Slice index utilities are provided by SliceIndexing.
}
