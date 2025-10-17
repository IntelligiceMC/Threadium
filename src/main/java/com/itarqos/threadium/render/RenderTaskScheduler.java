package com.itarqos.threadium.render;

import com.itarqos.threadium.util.ThreadiumLog;
import net.minecraft.client.MinecraftClient;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Smart "to-do list" for rendering tasks.
 * Splits rendering work into small tasks and spreads them across frames to prevent FPS spikes.
 */
public class RenderTaskScheduler {
    private static final RenderTaskScheduler INSTANCE = new RenderTaskScheduler();
    
    // Task queues by priority
    private final Queue<RenderTask> highPriorityTasks = new ConcurrentLinkedQueue<>();
    private final Queue<RenderTask> normalPriorityTasks = new ConcurrentLinkedQueue<>();
    private final Queue<RenderTask> lowPriorityTasks = new ConcurrentLinkedQueue<>();
    
    // Performance tracking
    private long frameStartTime = 0;
    private long maxFrameTimeNanos = 16_666_666; // ~16.67ms for 60 FPS target
    private int tasksProcessedThisFrame = 0;
    private int tasksSkippedThisFrame = 0;
    private int totalTasksProcessed = 0;
    private int totalTasksSkipped = 0;
    
    // Statistics for logging
    private long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 5000; // Log every 5 seconds
    
    private RenderTaskScheduler() {}
    
    public static RenderTaskScheduler get() {
        return INSTANCE;
    }
    
    /**
     * Call this at the start of each frame
     */
    public void beginFrame() {
        frameStartTime = System.nanoTime();
        tasksProcessedThisFrame = 0;
        tasksSkippedThisFrame = 0;
        
        // Periodic logging
        long now = System.currentTimeMillis();
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            logStatistics();
            lastLogTime = now;
        }
    }
    
    /**
     * Call this at the end of each frame
     */
    public void endFrame() {
        totalTasksProcessed += tasksProcessedThisFrame;
        totalTasksSkipped += tasksSkippedThisFrame;
    }
    
    /**
     * Submit a rendering task to be processed
     */
    public void submitTask(RenderTask task) {
        if (task == null) return;
        
        switch (task.priority) {
            case HIGH -> highPriorityTasks.offer(task);
            case NORMAL -> normalPriorityTasks.offer(task);
            case LOW -> lowPriorityTasks.offer(task);
        }
        
        ThreadiumLog.info("[RenderScheduler] Task submitted: %s (priority: %s)", 
            task.name, task.priority);
    }
    
    /**
     * Process tasks for this frame, respecting time budget
     */
    public void processTasks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        
        // Adjust time budget based on current FPS
        int targetFps = mc.options.getMaxFps().getValue();
        if (targetFps > 0 && targetFps < 260) {
            maxFrameTimeNanos = 1_000_000_000L / targetFps;
        }
        
        // Process high priority first
        processQueue(highPriorityTasks, "HIGH");
        
        // Then normal priority if we have time
        if (hasTimeRemaining()) {
            processQueue(normalPriorityTasks, "NORMAL");
        }
        
        // Finally low priority if we still have time
        if (hasTimeRemaining()) {
            processQueue(lowPriorityTasks, "LOW");
        }
    }
    
    private void processQueue(Queue<RenderTask> queue, String priorityName) {
        while (!queue.isEmpty() && hasTimeRemaining()) {
            RenderTask task = queue.poll();
            if (task == null) break;
            
            // Check if task is still relevant
            if (!task.isRelevant()) {
                tasksSkippedThisFrame++;
                ThreadiumLog.info("[RenderScheduler] Skipped irrelevant task: %s", task.name);
                continue;
            }
            
            long taskStart = System.nanoTime();
            try {
                task.execute();
                tasksProcessedThisFrame++;
                long taskDuration = System.nanoTime() - taskStart;
                ThreadiumLog.info("[RenderScheduler] Executed %s task '%s' in %.2fms", 
                    priorityName, task.name, taskDuration / 1_000_000.0);
            } catch (Exception e) {
                ThreadiumLog.error("[RenderScheduler] Task '%s' failed: %s", 
                    task.name, e.getMessage());
            }
        }
    }
    
    private boolean hasTimeRemaining() {
        long elapsed = System.nanoTime() - frameStartTime;
        // Use 80% of frame time budget to leave room for other rendering
        return elapsed < (maxFrameTimeNanos * 0.8);
    }
    
    private void logStatistics() {
        int queuedHigh = highPriorityTasks.size();
        int queuedNormal = normalPriorityTasks.size();
        int queuedLow = lowPriorityTasks.size();
        int totalQueued = queuedHigh + queuedNormal + queuedLow;
        
        ThreadiumLog.info("[RenderScheduler] Stats - Queued: %d (H:%d N:%d L:%d), Processed: %d, Skipped: %d",
            totalQueued, queuedHigh, queuedNormal, queuedLow, 
            totalTasksProcessed, totalTasksSkipped);
        
        // Reset counters
        totalTasksProcessed = 0;
        totalTasksSkipped = 0;
    }
    
    /**
     * Clear all pending tasks (e.g., on world change)
     */
    public void clear() {
        int cleared = highPriorityTasks.size() + normalPriorityTasks.size() + lowPriorityTasks.size();
        highPriorityTasks.clear();
        normalPriorityTasks.clear();
        lowPriorityTasks.clear();
        ThreadiumLog.info("[RenderScheduler] Cleared %d pending tasks", cleared);
    }
    
    public int getQueuedTaskCount() {
        return highPriorityTasks.size() + normalPriorityTasks.size() + lowPriorityTasks.size();
    }
    
    /**
     * Represents a single rendering task
     */
    public static abstract class RenderTask {
        protected final String name;
        protected final Priority priority;
        protected final long createdAt;
        
        public RenderTask(String name, Priority priority) {
            this.name = name;
            this.priority = priority;
            this.createdAt = System.currentTimeMillis();
        }
        
        /**
         * Execute the rendering work
         */
        public abstract void execute();
        
        /**
         * Check if this task is still relevant (not stale)
         * Override to implement custom relevance checks
         */
        public boolean isRelevant() {
            // Tasks older than 5 seconds are considered stale
            return (System.currentTimeMillis() - createdAt) < 5000;
        }
    }
    
    public enum Priority {
        HIGH,   // Must be done this frame (visible chunks, nearby entities)
        NORMAL, // Should be done soon (particles, effects)
        LOW     // Can wait (distant chunks, background updates)
    }
}
