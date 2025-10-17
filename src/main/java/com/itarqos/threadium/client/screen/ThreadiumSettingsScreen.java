package com.itarqos.threadium.client.screen;

import com.itarqos.threadium.client.ThreadiumClient;
import com.itarqos.threadium.config.ThreadiumConfig;
import com.itarqos.threadium.util.ThreadiumLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public class ThreadiumSettingsScreen extends Screen {
    private final Screen parent;
    private ThreadiumConfig cfg;
    private Category category = Category.ENTITIES;
    
    // Scrolling support
    private double scrollOffset = 0.0;
    private double maxScroll = 0.0;
    private static final int SCROLL_SPEED = 10;
    // Scrollbar interaction state (computed each render)
    private boolean draggingScrollbar = false;
    private double dragStartY = 0.0;
    private double dragStartScroll = 0.0;
    // Cache last-drawn scrollbar geometry for hit-testing
    private int sbX, sbY, sbH;
    private double sbThumbY, sbThumbH;

    private enum Category {
        ENTITIES, WORLD, ADVANCED
    }

    private class TargetFpsSlider extends SliderWidget {
        private static final int MIN = 30;
        private static final int MAX = 240;
        TargetFpsSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }
        @Override protected void updateMessage() { this.setMessage(Text.of(label(current()))); }
        @Override protected void applyValue() { cfg.targetFps = current(); ThreadiumClient.saveConfig(); }
        private static double normalize(int v) { return (clamp(v) - (double)MIN) / (double)(MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.target_fps", clamp(v)).getString(); }
    }

    private class QosAggressivenessSlider extends SliderWidget {
        private static final double MIN = 0.0;
        private static final double MAX = 1.0;
        QosAggressivenessSlider(int x, int y, int width, int height, float initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }
        @Override protected void updateMessage() { this.setMessage(Text.of(label((float)current()))); }
        @Override protected void applyValue() { cfg.qosAggressiveness = current(); ThreadiumClient.saveConfig(); }
        private static double normalize(float v) { return (clamp(v) - MIN) / (MAX - MIN); }
        private double current() { return MIN + this.value * (MAX - MIN); }
        private static float clamp(float v) { return (float)Math.max(MIN, Math.min(MAX, v)); }
        private static String label(float v) { return Text.translatable("threadium.settings.qos_aggressiveness", String.format("%.2f", clamp(v))).getString(); }
    }

    private class SliceBudgetSlider extends SliderWidget {
        private static final int MIN = 1;
        private static final int MAX = 64;
        SliceBudgetSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }
        @Override protected void updateMessage() { this.setMessage(Text.of(label(current()))); }
        @Override protected void applyValue() { cfg.sliceBudgetPerTick = current(); ThreadiumClient.saveConfig(); }
        private static double normalize(int v) { return (clamp(v) - (double)MIN) / (double)(MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.slice_budget_per_tick", clamp(v)).getString(); }
    }

    private class TurnBiasStrengthSlider extends SliderWidget {
        private static final double MIN = 0.0;
        private static final double MAX = 1.0;
        TurnBiasStrengthSlider(int x, int y, int width, int height, float initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }
        @Override protected void updateMessage() { this.setMessage(Text.of(label((float)current()))); }
        @Override protected void applyValue() { cfg.turnBiasStrength = current(); ThreadiumClient.saveConfig(); }
        private static double normalize(float v) { return (clamp(v) - MIN) / (MAX - MIN); }
        private double current() { return MIN + this.value * (MAX - MIN); }
        private static float clamp(float v) { return (float)Math.max(MIN, Math.min(MAX, v)); }
        private static String label(float v) { return Text.translatable("threadium.settings.turn_bias_strength", String.format("%.2f", clamp(v))).getString(); }
    }

    private class MicroStutterThresholdSlider extends SliderWidget {
        private static final int MIN = 10;
        private static final int MAX = 50;
        MicroStutterThresholdSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }
        @Override protected void updateMessage() { this.setMessage(Text.of(label(current()))); }
        @Override protected void applyValue() { cfg.microStutterThresholdMs = current(); ThreadiumClient.saveConfig(); }
        private static double normalize(int v) { return (clamp(v) - (double)MIN) / (double)(MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.micro_stutter_threshold", clamp(v)).getString(); }
    }

    private class ParticleTileBudgetSlider extends SliderWidget {
        private static final int MIN = 1;
        private static final int MAX = 32;
        ParticleTileBudgetSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }
        @Override protected void updateMessage() { this.setMessage(Text.of(label(current()))); }
        @Override protected void applyValue() { cfg.particleTileBudget = current(); ThreadiumClient.saveConfig(); }
        private static double normalize(int v) { return (clamp(v) - (double)MIN) / (double)(MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.particle_tile_budget_value", clamp(v)).getString(); }
    }

    private class ParticleTileSizeSlider extends SliderWidget {
        private static final int MIN = 4;
        private static final int MAX = 64;
        ParticleTileSizeSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }
        @Override protected void updateMessage() { this.setMessage(Text.of(label(current()))); }
        @Override protected void applyValue() { cfg.particleTileSize = current(); ThreadiumClient.saveConfig(); }
        private static double normalize(int v) { return (clamp(v) - (double)MIN) / (double)(MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.particle_tile_size", clamp(v)).getString(); }
    }

    private class PredictionAheadSlider extends SliderWidget {
        private static final int MIN = 0;
        private static final int MAX = 6;

        PredictionAheadSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() { this.setMessage(Text.of(label(current()))); }

        @Override
        protected void applyValue() {
            cfg.predictionAheadTicks = current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(int v) { return (clamp(v) - (double) MIN) / (double) (MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.prediction_ahead_ticks", clamp(v)).getString(); }
    }

    public ThreadiumSettingsScreen(Screen parent) {
        super(Text.translatable("threadium.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.cfg = ThreadiumClient.CONFIG;
        
        // Tabs
        int tabsY = this.height / 6 - 8;
        int center = this.width / 2;
        addTab(center - 170, tabsY, 110, Category.ENTITIES, Text.translatable("threadium.settings.tab.entities").getString());
        addTab(center - 40, tabsY, 110, Category.WORLD, Text.translatable("threadium.settings.tab.world").getString());
        addTab(center + 90, tabsY, 110, Category.ADVANCED, Text.translatable("threadium.settings.tab.advanced").getString());

        int left = this.width / 2 - 155;
        int colW = 310;
        int y = this.height / 6 + 24 - (int)scrollOffset;
        int visibleTop = this.height / 6 + 20;
        int visibleBottom = this.height - 40; // Leave space for Done button

        switch (category) {
            case ENTITIES -> {
                // Entity culling master
                addIfVisible(ButtonWidget.builder(entityCullingLabel(), b -> {
                    cfg.enableEntityCulling = !cfg.enableEntityCulling;
                    b.setMessage(entityCullingLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.entity_culling")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Behind culling
                addIfVisible(ButtonWidget.builder(entityBehindLabel(), b -> {
                    cfg.enableEntityBehindCulling = !cfg.enableEntityBehindCulling;
                    b.setMessage(entityBehindLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.behind_culling")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Vertical band culling for entities
                addIfVisible(ButtonWidget.builder(entityBandLabel(), b -> {
                    cfg.enableEntityVerticalBandCulling = !cfg.enableEntityVerticalBandCulling;
                    b.setMessage(entityBandLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.entity_y_band")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Distance slider for entity culling
                addIfVisible(new DistanceSlider(left, y, colW, 20, cfg.entityCullingDistance), y, 20, visibleTop, visibleBottom);
                y += 28;
            }
            case WORLD -> {
                // Chunk culling master
                addIfVisible(ButtonWidget.builder(chunkCullingLabel(), b -> {
                    cfg.enableChunkCulling = !cfg.enableChunkCulling;
                    b.setMessage(chunkCullingLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.chunk_culling")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Chunk vertical band
                addIfVisible(ButtonWidget.builder(chunkBandLabel(), b -> {
                    cfg.enableChunkVerticalBandCulling = !cfg.enableChunkVerticalBandCulling;
                    b.setMessage(chunkBandLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.chunk_y_band")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Block entity culling
                addIfVisible(ButtonWidget.builder(blockEntityCullingLabel(), b -> {
                    cfg.enableBlockEntityCulling = !cfg.enableBlockEntityCulling;
                    b.setMessage(blockEntityCullingLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.blockentity_culling")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Shared vertical band half-height slider
                addIfVisible(new VerticalBandSlider(left, y, colW, 20, cfg.verticalBandHalfHeight), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Dynamic vertical band toggle
                addIfVisible(ButtonWidget.builder(dynamicBandLabel(), b -> {
                    cfg.enableDynamicVerticalBand = !cfg.enableDynamicVerticalBand;
                    b.setMessage(dynamicBandLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.dynamic_y_band")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
            }
            case ADVANCED -> {
                // Overlay toggle
                addIfVisible(ButtonWidget.builder(overlayLabel(), b -> {
                    cfg.showCullingOverlay = !cfg.showCullingOverlay;
                    b.setMessage(overlayLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.overlay")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Debug mode toggle (forces counters even without F3)
                addIfVisible(ButtonWidget.builder(debugModeLabel(), b -> {
                    cfg.enableDebugMode = !cfg.enableDebugMode;
                    b.setMessage(debugModeLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.debug_mode")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Predictive prefetch
                addIfVisible(ButtonWidget.builder(predictivePrefetchLabel(), b -> {
                    cfg.enablePredictivePrefetch = !cfg.enablePredictivePrefetch;
                    b.setMessage(predictivePrefetchLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.predictive_prefetch")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // LOD throttling toggle
                addIfVisible(ButtonWidget.builder(lodThrottlingLabel(), b -> {
                    cfg.lodThrottlingEnabled = !cfg.lodThrottlingEnabled;
                    b.setMessage(lodThrottlingLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.lod_throttling")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Frustum hysteresis slider (0..6 ticks)
                addIfVisible(new HysteresisSlider(left, y, colW, 20, cfg.frustumHysteresisTicks), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Slice debounce slider (50..600 ms)
                addIfVisible(new DebounceSlider(left, y, colW, 20, cfg.sliceDebounceMillis), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Partial meshing master toggle
                addIfVisible(ButtonWidget.builder(partialMeshingLabel(), b -> {
                    cfg.enablePartialMeshing = !cfg.enablePartialMeshing;
                    b.setMessage(partialMeshingLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.partial_meshing")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                
                // Render scheduler toggle
                addIfVisible(ButtonWidget.builder(renderSchedulerLabel(), b -> {
                    cfg.enableRenderScheduler = !cfg.enableRenderScheduler;
                    b.setMessage(renderSchedulerLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.render_scheduler")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                
                // Verbose logging toggle
                addIfVisible(ButtonWidget.builder(verboseLoggingLabel(), b -> {
                    cfg.verboseLogging = !cfg.verboseLogging;
                    b.setMessage(verboseLoggingLabel());
                    ThreadiumLog.setVerbose(cfg.verboseLogging);
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.verbose_logging")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Prediction everywhere toggle
                addIfVisible(ButtonWidget.builder(Text.translatable("threadium.settings.prediction_everywhere", Text.translatable(cfg.enablePredictionEverywhere ? "threadium.common.on" : "threadium.common.off")), b -> {
                    cfg.enablePredictionEverywhere = !cfg.enablePredictionEverywhere;
                    b.setMessage(Text.translatable("threadium.settings.prediction_everywhere", Text.translatable(cfg.enablePredictionEverywhere ? "threadium.common.on" : "threadium.common.off")));
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.prediction_everywhere")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;

                // Prediction ahead ticks slider
                addIfVisible(new PredictionAheadSlider(left, y, colW, 20, cfg.predictionAheadTicks), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Visibility deprioritization toggle & sliders
                addIfVisible(ButtonWidget.builder(Text.translatable("threadium.settings.visibility_deprioritization", Text.translatable(cfg.enableVisibilityDeprioritization ? "threadium.common.on" : "threadium.common.off")), b -> {
                    cfg.enableVisibilityDeprioritization = !cfg.enableVisibilityDeprioritization;
                    b.setMessage(Text.translatable("threadium.settings.visibility_deprioritization", Text.translatable(cfg.enableVisibilityDeprioritization ? "threadium.common.on" : "threadium.common.off")));
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.visibility_deprioritization")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                addIfVisible(new HiddenFramesSlider(left, y, colW, 20, cfg.hiddenDeprioritizeFrames), y, 20, visibleTop, visibleBottom);
                y += 28;
                addIfVisible(new UnhidePerTickSlider(left, y, colW, 20, cfg.unhidePerTick), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Particle scaling toggle & sliders
                addIfVisible(ButtonWidget.builder(Text.translatable("threadium.settings.particle_distance_scaling", Text.translatable(cfg.particleDistanceScaling ? "threadium.common.on" : "threadium.common.off")), b -> {
                    cfg.particleDistanceScaling = !cfg.particleDistanceScaling;
                    b.setMessage(Text.translatable("threadium.settings.particle_distance_scaling", Text.translatable(cfg.particleDistanceScaling ? "threadium.common.on" : "threadium.common.off")));
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.particle_distance_scaling")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                addIfVisible(new ParticleNearSlider(left, y, colW, 20, cfg.particleNear), y, 20, visibleTop, visibleBottom);
                y += 28;
                addIfVisible(new ParticleFarSlider(left, y, colW, 20, cfg.particleFar), y, 20, visibleTop, visibleBottom);
                y += 28;
                addIfVisible(new ParticleMinDensitySlider(left, y, colW, 20, cfg.particleMinDensity), y, 20, visibleTop, visibleBottom);
                y += 28;
                addIfVisible(new ParticleFarSizeScaleSlider(left, y, colW, 20, cfg.particleFarSizeScale), y, 20, visibleTop, visibleBottom);
                y += 28;

                // QoS controller: target FPS & aggressiveness
                addIfVisible(new TargetFpsSlider(left, y, colW, 20, cfg.targetFps), y, 20, visibleTop, visibleBottom);
                y += 28;
                addIfVisible(new QosAggressivenessSlider(left, y, colW, 20, (float)cfg.qosAggressiveness), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Screen-space budgeter toggle & slice budget per tick
                addIfVisible(ButtonWidget.builder(screenSpaceBudgeterLabel(), b -> {
                    cfg.enableScreenSpaceBudgeter = !cfg.enableScreenSpaceBudgeter;
                    b.setMessage(screenSpaceBudgeterLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.screen_space_budgeter")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                addIfVisible(new SliceBudgetSlider(left, y, colW, 20, cfg.sliceBudgetPerTick), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Turn-bias prefetch toggle & strength
                addIfVisible(ButtonWidget.builder(turnBiasLabel(), b -> {
                    cfg.enableTurnBiasPrefetch = !cfg.enableTurnBiasPrefetch;
                    b.setMessage(turnBiasLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.turn_bias_prefetch")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                addIfVisible(new TurnBiasStrengthSlider(left, y, colW, 20, (float)cfg.turnBiasStrength), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Micro-stutter guard toggle & threshold
                addIfVisible(ButtonWidget.builder(microStutterLabel(), b -> {
                    cfg.enableMicroStutterGuard = !cfg.enableMicroStutterGuard;
                    b.setMessage(microStutterLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.micro_stutter_guard")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                addIfVisible(new MicroStutterThresholdSlider(left, y, colW, 20, cfg.microStutterThresholdMs), y, 20, visibleTop, visibleBottom);
                y += 28;

                // Particle tile budgeting toggle & sliders
                addIfVisible(ButtonWidget.builder(particleTileBudgetLabel(), b -> {
                    cfg.enableParticleTileBudget = !cfg.enableParticleTileBudget;
                    b.setMessage(particleTileBudgetLabel());
                    ThreadiumClient.saveConfig();
                }).tooltip(Tooltip.of(Text.translatable("threadium.tooltip.particle_tile_budget")))
                  .dimensions(left, y, colW, 20).build(), y, 20, visibleTop, visibleBottom);
                y += 24;
                addIfVisible(new ParticleTileBudgetSlider(left, y, colW, 20, cfg.particleTileBudget), y, 20, visibleTop, visibleBottom);
                y += 28;
                addIfVisible(new ParticleTileSizeSlider(left, y, colW, 20, cfg.particleTileSize), y, 20, visibleTop, visibleBottom);
                y += 28;
            }
        }

        // Done button (bottom) - fixed position, not affected by scroll
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("threadium.settings.done"), b -> {
            ThreadiumClient.saveConfig();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
        
        // Calculate max scroll based on content height
        int contentBottom = y + (int)scrollOffset;
        maxScroll = Math.max(0, contentBottom - visibleBottom);
        // Ensure current offset stays within new bounds after rebuild
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }
    @Override
    public void close() {
        ThreadiumClient.saveConfig();
        MinecraftClient.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Scroll the content
        double newOffset = scrollOffset - (verticalAmount * SCROLL_SPEED);
        scrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
        
        // Rebuild widgets with new scroll offset
        this.clearAndInit();
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0) {
            // Hit-test scrollbar track
            if (mouseX >= sbX && mouseX <= sbX + 6 && mouseY >= sbY && mouseY <= sbY + sbH) {
                // If inside thumb, start dragging; else jump to position
                if (mouseY >= sbThumbY && mouseY <= sbThumbY + sbThumbH) {
                    draggingScrollbar = true;
                    dragStartY = mouseY;
                    dragStartScroll = scrollOffset;
                } else {
                    // Jump: center thumb around click
                    double range = sbH - sbThumbH;
                    double t = (mouseY - sbY - sbThumbH * 0.5);
                    t = Math.max(0, Math.min(range, t));
                    scrollOffset = Math.max(0, Math.min(maxScroll, (t / range) * maxScroll));
                    this.clearAndInit();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && maxScroll > 0) {
            // Map drag to scroll offset
            double range = sbH - sbThumbH;
            double initialThumb = (dragStartScroll / maxScroll) * range;
            double newThumb = Math.max(0, Math.min(range, initialThumb + (mouseY - dragStartY)));
            scrollOffset = Math.max(0, Math.min(maxScroll, (newThumb / range) * maxScroll));
            this.clearAndInit();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        
        // Render widgets
        super.render(context, mouseX, mouseY, delta);
        
        // Draw scrollbar if needed
        if (maxScroll > 0) {
            drawScrollbar(context);
        }
    }
    
    private void drawScrollbar(DrawContext context) {
        int scrollbarX = this.width - 10;
        int scrollbarY = this.height / 6 + 20;
        int scrollbarHeight = this.height - scrollbarY - 40;
        
        // Background track
        context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0x80000000);
        
        // Calculate thumb position and size
        double contentHeight = maxScroll + scrollbarHeight;
        double thumbHeight = Math.max(20, scrollbarHeight * scrollbarHeight / contentHeight);
        double thumbY = scrollbarY + (scrollOffset / maxScroll) * (scrollbarHeight - thumbHeight);
        
        // Thumb
        context.fill(scrollbarX, (int)thumbY, scrollbarX + 6, (int)(thumbY + thumbHeight), 0xFFAAAAAA);
        // Cache geometry
        this.sbX = scrollbarX; this.sbY = scrollbarY; this.sbH = scrollbarHeight;
        this.sbThumbY = thumbY; this.sbThumbH = thumbHeight;
    }

    private void addIfVisible(ButtonWidget widget, int y, int h, int visibleTop, int visibleBottom) {
        if (y + h >= visibleTop && y <= visibleBottom) {
            this.addDrawableChild(widget);
        }
    }

    private void addIfVisible(SliderWidget widget, int y, int h, int visibleTop, int visibleBottom) {
        if (y + h >= visibleTop && y <= visibleBottom) {
            this.addDrawableChild(widget);
        }
    }

    private void addTab(int x, int y, int w, Category cat, String label) {
        Text text = Text.of((this.category == cat ? "> " : "") + label);
        this.addDrawableChild(ButtonWidget.builder(text, b -> {
            this.category = cat;
            this.scrollOffset = 0.0; // Reset scroll when changing tabs
            this.clearAndInit();
        }).dimensions(x, y, w, 20).build());
    }

    private Text entityCullingLabel() {
        return Text.translatable("threadium.settings.entity_culling", Text.translatable(cfg.enableEntityCulling ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text chunkCullingLabel() {
        return Text.translatable("threadium.settings.chunk_culling", Text.translatable(cfg.enableChunkCulling ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text overlayLabel() {
        return Text.translatable("threadium.settings.overlay", Text.translatable(cfg.showCullingOverlay ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text debugModeLabel() { return Text.translatable("threadium.settings.debug_mode", Text.translatable(cfg.enableDebugMode ? "threadium.common.on" : "threadium.common.off")); }

    private Text entityBehindLabel() {
        return Text.translatable("threadium.settings.behind_culling", Text.translatable(cfg.enableEntityBehindCulling ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text entityBandLabel() {
        return Text.translatable("threadium.settings.entity_y_band", Text.translatable(cfg.enableEntityVerticalBandCulling ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text chunkBandLabel() {
        return Text.translatable("threadium.settings.chunk_y_band", Text.translatable(cfg.enableChunkVerticalBandCulling ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text blockEntityCullingLabel() {
        return Text.translatable("threadium.settings.blockentity_culling", Text.translatable(cfg.enableBlockEntityCulling ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text dynamicBandLabel() {
        return Text.translatable("threadium.settings.dynamic_y_band", Text.translatable(cfg.enableDynamicVerticalBand ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text predictivePrefetchLabel() {
        return Text.translatable("threadium.settings.predictive_prefetch", Text.translatable(cfg.enablePredictivePrefetch ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text lodThrottlingLabel() {
        return Text.translatable("threadium.settings.lod_throttling", Text.translatable(cfg.lodThrottlingEnabled ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text partialMeshingLabel() {
        return Text.translatable("threadium.settings.partial_meshing", Text.translatable(cfg.enablePartialMeshing ? "threadium.common.on" : "threadium.common.off"));
    }
    
    private Text renderSchedulerLabel() {
        return Text.translatable("threadium.settings.render_scheduler", Text.translatable(cfg.enableRenderScheduler ? "threadium.common.on" : "threadium.common.off"));
    }
    
    private Text verboseLoggingLabel() {
        return Text.translatable("threadium.settings.verbose_logging", Text.translatable(cfg.verboseLogging ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text screenSpaceBudgeterLabel() {
        return Text.translatable("threadium.settings.screen_space_budgeter", Text.translatable(cfg.enableScreenSpaceBudgeter ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text turnBiasLabel() {
        return Text.translatable("threadium.settings.turn_bias_prefetch", Text.translatable(cfg.enableTurnBiasPrefetch ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text microStutterLabel() {
        return Text.translatable("threadium.settings.micro_stutter_guard", Text.translatable(cfg.enableMicroStutterGuard ? "threadium.common.on" : "threadium.common.off"));
    }

    private Text particleTileBudgetLabel() {
        return Text.translatable("threadium.settings.particle_tile_budget", Text.translatable(cfg.enableParticleTileBudget ? "threadium.common.on" : "threadium.common.off"));
    }

    private class DistanceSlider extends SliderWidget {
        private static final double MIN = 32.0;
        private static final double MAX = 256.0;

        DistanceSlider(int x, int y, int width, int height, double initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.of(label(current())));
        }

        @Override
        protected void applyValue() {
            cfg.entityCullingDistance = current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(double v) {
            return (clamp(v) - MIN) / (MAX - MIN);
        }

        private double current() {
            return MIN + this.value * (MAX - MIN);
        }

        private static double clamp(double v) {
            if (v < MIN) return MIN;
            if (v > MAX) return MAX;
            return v;
        }

        private static String label(double v) {
            return Text.translatable("threadium.settings.entity_distance", String.format("%.0f", clamp(v))).getString();
        }
    }

    private class VerticalBandSlider extends SliderWidget {
        private static final int MIN = 5;
        private static final int MAX = 50;

        VerticalBandSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(bandLabel(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.of(bandLabel(current())));
        }

        @Override
        protected void applyValue() {
            cfg.verticalBandHalfHeight = current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(int v) {
            return (clamp(v) - (double) MIN) / (double) (MAX - MIN);
        }

        private int current() {
            int v = (int) Math.round(MIN + this.value * (MAX - MIN));
            return clamp(v);
        }

        private static int clamp(int v) {
            if (v < MIN) return MIN;
            if (v > MAX) return MAX;
            return v;
        }

        private static String bandLabel(int v) {
            return Text.translatable("threadium.settings.vertical_band_half", clamp(v)).getString();
        }
    }

    private class HysteresisSlider extends SliderWidget {
        private static final int MIN = 0;
        private static final int MAX = 6;

        HysteresisSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.of(label(current())));
        }

        @Override
        protected void applyValue() {
            cfg.frustumHysteresisTicks = current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(int v) { return (clamp(v) - (double) MIN) / (double) (MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.hysteresis", clamp(v)).getString(); }
    }

    private class DebounceSlider extends SliderWidget {
        private static final int MIN = 50;
        private static final int MAX = 600;

        DebounceSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.of(label(current())));
        }

        @Override
        protected void applyValue() {
            cfg.sliceDebounceMillis = current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(int v) { return (clamp(v) - (double) MIN) / (double) (MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.slice_debounce", clamp(v)).getString(); }
    }

    private class HiddenFramesSlider extends SliderWidget {
        private static final int MIN = 0;
        private static final int MAX = 60;

        HiddenFramesSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() { this.setMessage(Text.of(label(current()))); }

        @Override
        protected void applyValue() {
            cfg.hiddenDeprioritizeFrames = current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(int v) { return (clamp(v) - (double) MIN) / (double) (MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.hidden_deprioritize", clamp(v)).getString(); }
    }

    private class UnhidePerTickSlider extends SliderWidget {
        private static final int MIN = 1;
        private static final int MAX = 64;

        UnhidePerTickSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() { this.setMessage(Text.of(label(current()))); }

        @Override
        protected void applyValue() {
            cfg.unhidePerTick = current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(int v) { return (clamp(v) - (double) MIN) / (double) (MAX - MIN); }
        private int current() { return clamp((int)Math.round(MIN + this.value * (MAX - MIN))); }
        private static int clamp(int v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(int v) { return Text.translatable("threadium.settings.unhide_per_tick", clamp(v)).getString(); }
    }

    private class ParticleNearSlider extends SliderWidget {
        private static final double MIN = 4.0;
        private static final double MAX = 64.0;

        ParticleNearSlider(int x, int y, int width, int height, double initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() { this.setMessage(Text.of(label(current()))); }

        @Override
        protected void applyValue() {
            cfg.particleNear = current();
            // keep far >= near + 1
            if (cfg.particleFar < cfg.particleNear + 1.0) cfg.particleFar = cfg.particleNear + 1.0;
            ThreadiumClient.saveConfig();
        }

        private static double normalize(double v) { return (clamp(v) - MIN) / (MAX - MIN); }
        private double current() { return MIN + this.value * (MAX - MIN); }
        private static double clamp(double v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(double v) { return Text.translatable("threadium.settings.particle_near", String.format("%.1f", clamp(v))).getString(); }
    }

    private class ParticleFarSlider extends SliderWidget {
        private static final double MIN = 16.0;
        private static final double MAX = 128.0;

        ParticleFarSlider(int x, int y, int width, int height, double initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() { this.setMessage(Text.of(label(current()))); }

        @Override
        protected void applyValue() {
            cfg.particleFar = Math.max(current(), cfg.particleNear + 1.0);
            ThreadiumClient.saveConfig();
        }

        private static double normalize(double v) { return (clamp(v) - MIN) / (MAX - MIN); }
        private double current() { return MIN + this.value * (MAX - MIN); }
        private static double clamp(double v) { return Math.max(MIN, Math.min(MAX, v)); }
        private static String label(double v) { return Text.translatable("threadium.settings.particle_far", String.format("%.1f", clamp(v))).getString(); }
    }

    private class ParticleMinDensitySlider extends SliderWidget {
        private static final double MIN = 0.1;
        private static final double MAX = 1.0;

        ParticleMinDensitySlider(int x, int y, int width, int height, float initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() { this.setMessage(Text.of(label((float) current()))); }

        @Override
        protected void applyValue() {
            cfg.particleMinDensity = (float) current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(float v) { return (clamp(v) - MIN) / (MAX - MIN); }
        private double current() { return MIN + this.value * (MAX - MIN); }
        private static float clamp(float v) { return (float)Math.max(MIN, Math.min(MAX, v)); }
        private static String label(float v) { return Text.translatable("threadium.settings.particle_min_density", String.format("%.2f", clamp(v))).getString(); }
    }

    private class ParticleFarSizeScaleSlider extends SliderWidget {
        private static final double MIN = 0.2;
        private static final double MAX = 1.0;

        ParticleFarSizeScaleSlider(int x, int y, int width, int height, float initial) {
            super(x, y, width, height, Text.of(label(initial)), normalize(initial));
        }

        @Override
        protected void updateMessage() { this.setMessage(Text.of(label((float) current()))); }

        @Override
        protected void applyValue() {
            cfg.particleFarSizeScale = (float) current();
            ThreadiumClient.saveConfig();
        }

        private static double normalize(float v) { return (clamp(v) - MIN) / (MAX - MIN); }
        private double current() { return MIN + this.value * (MAX - MIN); }
        private static float clamp(float v) { return (float)Math.max(MIN, Math.min(MAX, v)); }
        private static String label(float v) { return Text.translatable("threadium.settings.particle_far_size_scale", String.format("%.2f", clamp(v))).getString(); }
    }
}
