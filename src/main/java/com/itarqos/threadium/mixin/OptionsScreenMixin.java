package com.itarqos.threadium.mixin;

import com.itarqos.threadium.client.screen.ThreadiumSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
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

        if (telemetry == null) return;

        int x = telemetry.getX();
        int y = telemetry.getY();
        int w = telemetry.getWidth();
        int h = telemetry.getHeight();

        telemetry.visible = false;
        telemetry.active = false;

        Screen screen = (Screen)(Object)this; // current screen as parent for the settings screen
        this.addDrawableChild(ButtonWidget.builder(Text.of("Threadium"), b -> {
            MinecraftClient.getInstance().setScreen(new ThreadiumSettingsScreen(screen));
        }).dimensions(x, y, w, h).build());
    }
}
