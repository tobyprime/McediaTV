package top.tobyprime.mcedia_mtv.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia.runtime.NativeRuntime;

public final class MtvNativeRuntimeScreen extends Screen {
    private String status = "mcedia_mtv.native_runtime.ready";

    private MtvNativeRuntimeScreen() { super(Component.translatable("mcedia_mtv.native_runtime.title")); }

    public static void open(Minecraft client) {
        if (client.screen == null) client.setScreen(new MtvNativeRuntimeScreen());
    }

    @Override protected void init() {
        int left = width / 2 - 150;
        addRenderableWidget(Button.builder(Component.translatable("mcedia_mtv.native_runtime.download"), button -> {
            button.active = false;
            status = "mcedia_mtv.native_runtime.downloading";
            NativeRuntime.downloadAsync().thenAccept(state -> {
                status = state == NativeRuntime.State.READY ? "mcedia_mtv.native_runtime.success" : "mcedia_mtv.native_runtime.failed";
                if (minecraft != null) minecraft.execute(() -> button.active = state != NativeRuntime.State.DOWNLOADING);
            });
        }).bounds(left, height / 2 + 25, 300, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mcedia_mtv.native_runtime.close"), button -> minecraft.setScreen(null))
                .bounds(left, height / 2 + 52, 300, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 80, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("mcedia_mtv.native_runtime.message"), width / 2, height / 2 - 48, 0xFFD0D0D0);
        graphics.centeredText(font, Component.translatable("mcedia_mtv.native_runtime.platform", NativeRuntime.platform()), width / 2, height / 2 - 28, 0xFFD0D0D0);
        graphics.centeredText(font, Component.translatable(status), width / 2, height / 2, 0xFFFFD070);
    }
}
