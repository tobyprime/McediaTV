package top.tobyprime.mcedia_mtv.client.worldui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlSender;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCover;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCoverCache;

/** 1.21.11 full-screen local URL preview and playlist insertion controls. */
public final class MtvAddMediaScreen extends Screen {
    private final WorldUiInteractionState.Target target;
    private final WorldUiAddMediaModel model = new WorldUiAddMediaModel(top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache.getInstance());
    private EditBox input;
    private String preview;
    private Button prepend, insertNext, append, playNow;
    private MtvAddMediaScreen(WorldUiInteractionState.Target target) { super(Component.translatable("mcedia_mtv.add_media.title")); this.target = target; }
    public static void open(Minecraft client, WorldUiInteractionState.Target target) { if (target != null) client.setScreen(new MtvAddMediaScreen(target)); }
    @Override protected void init() {
        int left = width / 2 - 220;
        input = addRenderableWidget(new EditBox(font, left, height / 2 - 80, 440, 24, Component.literal("URL")));
        input.setMaxLength(2048); input.setHint(Component.translatable("mcedia_mtv.add_media.url_hint")); input.setResponder(model::setInput);
        prepend = addButton(left, height / 2 + 50, "mcedia_mtv.add_media.prepend", WorldUiAddMediaModel.AddMode.PREPEND);
        insertNext = addButton(left + 112, height / 2 + 50, "mcedia_mtv.add_media.insert_next", WorldUiAddMediaModel.AddMode.INSERT_NEXT);
        append = addButton(left + 224, height / 2 + 50, "mcedia_mtv.add_media.append", WorldUiAddMediaModel.AddMode.APPEND);
        playNow = addButton(left + 336, height / 2 + 50, "mcedia_mtv.add_media.play_now", WorldUiAddMediaModel.AddMode.INSERT_AND_PLAY);
        addRenderableWidget(Button.builder(Component.translatable("mcedia_mtv.add_media.cancel"), button -> minecraft.setScreen(null)).bounds(width / 2 - 52, height / 2 + 78, 104, 20).build());
        input.setFocused(true);
    }
    private Button addButton(int x, int y, String labelKey, WorldUiAddMediaModel.AddMode mode) { return addRenderableWidget(Button.builder(Component.translatable(labelKey), button -> model.confirm(target, WorldUiControlSender.getInstance(), mode)).bounds(x, y, 104, 20).build()); }
    @Override public void tick() {
        super.tick(); boolean enabled = model.canConfirm(); if (prepend != null) { prepend.active = enabled; insertNext.active = enabled; append.active = enabled; playNow.active = enabled; }
        model.onControlResult(WorldUiControlSender.getInstance().consumeLastResult());
        preview = model.previewMessage();
        if (model.consumeAccepted()) minecraft.setScreen(null);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // super.render draws the blurred background and widgets; a second explicit
        // renderBackground here would blur twice and crash on 1.21.11.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.translatable("mcedia_mtv.add_media.title"), width / 2, height / 2 - 110, 0xFFFFFFFF);
        drawPreviewCover(graphics);
        graphics.drawCenteredString(font, Component.literal(preview == null ? Component.translatable("mcedia_mtv.add_media.waiting").getString() : preview), width / 2, height / 2 - 40, 0xFFD0D0D0);
    }
    private void drawPreviewCover(GuiGraphics graphics) {
        MtvMediaMetadata metadata = model.preview();
        int x = width / 2 - 210, y = height / 2 - 45;
        graphics.fill(x, y, x + 64, y + 64, 0xFF333333);
        if (metadata == null) return;
        Identifier textureId = MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        if (textureId == null) return;
        MtvMediaCover cover = MtvMediaCoverCache.getInstance().cached(metadata.coverUrl());
        if (cover == null || cover.status() != MtvMediaCover.Status.RESOLVED || cover.width() <= 0 || cover.height() <= 0) return;
        // Fixed 64x64 frame; the image is contain-fitted (aspect preserved, maximized, centered).
        int frame = 64;
        int dw, dh;
        float aspect = (float) cover.width() / cover.height();
        if (aspect >= 1.0F) {
            dw = frame;
            dh = Math.max(1, Math.round(frame / aspect));
        } else {
            dh = frame;
            dw = Math.max(1, Math.round(frame * aspect));
        }
        int dx = x + (frame - dw) / 2, dy = y + (frame - dh) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, textureId, dx, dy, 0.0F, 0.0F, dw, dh,
                cover.width(), cover.height(), cover.width(), cover.height(), -1);
    }
}
