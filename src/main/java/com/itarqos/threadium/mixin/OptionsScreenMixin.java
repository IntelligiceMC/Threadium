package com.itarqos.threadium.mixin;

import com.itarqos.threadium.client.screen.ThreadiumSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void threadium$replaceTelemetryButton(CallbackInfo ci) {
        ClickableWidget telemetry = null;
        for (Element el : this.children()) {
            if (el instanceof ClickableWidget cw) {
                if (cw.getMessage().getString().toLowerCase().contains("telemetry")) {
                    telemetry = cw;
                    break;
                }
            }
        }
        Screen screen = (Screen)(Object)this; // current screen as parent for the settings screen

        if (telemetry == null) {
            // No telemetry button found: find a free grid slot and size based on existing widgets
            List<ClickableWidget> candidates = new ArrayList<>();
            for (Element el : this.children()) {
                if (el instanceof ClickableWidget cw) {
                    int cwW = cw.getWidth();
                    int cwH = cw.getHeight();
                    // Ignore bottom-wide buttons like "Done" and odd-sized controls
                    if (cwW <= 180 && cwH >= 18 && cwH <= 22) {
                        candidates.add(cw);
                    }
                }
            }

            int defaultW = 150, defaultH = 20, step = 24;
            int xPlace, yPlace, wPlace, hPlace;

            if (candidates.isEmpty()) {
                // Fallback to vanilla assumptions (two-column grid layout)
                int left = this.width / 2 - 155;
                xPlace = left;
                yPlace = this.height / 6 - 12;
                wPlace = defaultW;
                hPlace = defaultH;
            } else {
                // Derive columns (unique Xs) and typical size per column
                Set<Integer> xs = new HashSet<>();
                Map<Integer, Integer> colWidth = new HashMap<>();
                Map<Integer, Integer> colHeight = new HashMap<>();
                Set<Integer> ys = new HashSet<>();
                for (ClickableWidget cw : candidates) {
                    xs.add(cw.getX());
                    ys.add(cw.getY());
                    colWidth.putIfAbsent(cw.getX(), cw.getWidth());
                    colHeight.putIfAbsent(cw.getX(), cw.getHeight());
                }

                List<Integer> cols = new ArrayList<>(xs);
                Collections.sort(cols);
                // Limit to first two columns if many
                if (cols.size() > 2) cols = cols.subList(0, 2);

                List<Integer> rows = new ArrayList<>(ys);
                Collections.sort(rows);

                // Estimate step as the most common delta between consecutive rows
                int estimatedStep = step;
                if (rows.size() >= 2) {
                    Map<Integer, Integer> freq = new HashMap<>();
                    for (int i = 1; i < rows.size(); i++) {
                        int d = rows.get(i) - rows.get(i - 1);
                        if (d > 0 && d <= 30) {
                            freq.put(d, freq.getOrDefault(d, 0) + 1);
                        }
                    }
                    if (!freq.isEmpty()) {
                        estimatedStep = Collections.max(freq.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey();
                    }
                }

                step = estimatedStep;
                int minY = rows.get(0);

                // Helper to check occupancy with small tolerance
                final int tol = 2;
                java.util.function.BiPredicate<Integer, Integer> occupied = (xx, yy) -> {
                    for (ClickableWidget cw : candidates) {
                        if (Math.abs(cw.getX() - xx) <= tol && Math.abs(cw.getY() - yy) <= tol) return true;
                    }
                    return false;
                };

                // Find first free cell
                int tryRows = rows.size() + 2; // allow adding a new row
                int foundX = -1, foundY = -1;
                for (int r = 0; r < tryRows && foundX == -1; r++) {
                    int y = minY + r * step;
                    for (int x : cols) {
                        if (!occupied.test(x, y)) {
                            foundX = x;
                            foundY = y;
                            break;
                        }
                    }
                }

                if (foundX == -1) {
                    // No gaps; append a new row under the last existing one at first column
                    int lastY = rows.get(rows.size() - 1);
                    foundX = cols.get(0);
                    foundY = lastY + step;
                }

                xPlace = foundX;
                yPlace = foundY;
                wPlace = colWidth.getOrDefault(foundX, defaultW);
                hPlace = colHeight.getOrDefault(foundX, defaultH);

                // Ensure we don't overlap the bottom bar
                int bottomLimit = this.height - 40;
                if (yPlace + hPlace > bottomLimit) {
                    yPlace = Math.max(this.height / 6 - 12, bottomLimit - hPlace);
                }
            }

            this.addDrawableChild(ButtonWidget.builder(Text.of("Threadium"), b -> {
                MinecraftClient.getInstance().setScreen(new ThreadiumSettingsScreen(screen));
            }).dimensions(xPlace, yPlace, wPlace, hPlace).build());
            return;
        }

        int x = telemetry.getX();
        int y = telemetry.getY();
        int w = telemetry.getWidth();
        int h = telemetry.getHeight();

        telemetry.visible = false;
        telemetry.active = false;

        this.addDrawableChild(ButtonWidget.builder(Text.of("Threadium"), b -> {
            MinecraftClient.getInstance().setScreen(new ThreadiumSettingsScreen(screen));
        }).dimensions(x, y, w, h).build());
    }
}
