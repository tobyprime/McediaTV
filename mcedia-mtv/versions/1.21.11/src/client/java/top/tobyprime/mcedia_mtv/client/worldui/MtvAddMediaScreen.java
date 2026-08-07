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
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCoverCache;

/** 1.21.11 full-screen local URL preview and playlist insertion controls. */
public final class MtvAddMediaScreen extends Screen {
    private final WorldUiInteractionState.Target target;
    private final WorldUiAddMediaModel model = new WorldUiAddMediaModel(top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache.getInstance());
    private EditBox input;
    private String preview;
    private Button prepend, insertNext, append, playNow;
    private MtvAddMediaScreen(WorldUiInteractionState.Target target) { super(Component.literal("MTV / Add media")); this.target = target; }
    public static void open(Minecraft client, WorldUiInteractionState.Target target) { if (target != null) client.setScreen(new MtvAddMediaScreen(target)); }
    @Override protected void init() {
        int left = width / 2 - 220;
        input = addRenderableWidget(new EditBox(font, left, height / 2 - 80, 440, 24, Component.literal("URL")));
        input.setMaxLength(2048); input.setHint(Component.literal("Paste a supported media URL")); input.setResponder(model::setInput);
        prepend = addButton(left, height / 2 + 50, "Prepend", WorldUiAddMediaModel.AddMode.PREPEND);
        insertNext = addButton(left + 112, height / 2 + 50, "Insert next", WorldUiAddMediaModel.AddMode.INSERT_NEXT);
        append = addButton(left + 224, height / 2 + 50, "Append", WorldUiAddMediaModel.AddMode.APPEND);
        playNow = addButton(left + 336, height / 2 + 50, "Play now", WorldUiAddMediaModel.AddMode.INSERT_AND_PLAY);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> minecraft.setScreen(null)).bounds(width / 2 - 52, height / 2 + 78, 104, 20).build());
        input.setFocused(true);
    }
    private Button addButton(int x, int y, String label, WorldUiAddMediaModel.AddMode mode) { return addRenderableWidget(Button.builder(Component.literal(label), button -> model.confirm(target, WorldUiControlSender.getInstance(), mode)).bounds(x, y, 104, 20).build()); }
    @Override public void tick() {
        super.tick(); boolean enabled = model.canConfirm(); if (prepend != null) { prepend.active = enabled; insertNext.active = enabled; append.active = enabled; playNow.active = enabled; }
        model.onControlResult(WorldUiControlSender.getInstance().lastResult()); MtvMediaMetadata metadata = model.preview();
        preview = previewMessage(metadata);
        if (model.consumeAccepted()) minecraft.setScreen(null);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.literal("Add media to MTV playlist"), width / 2, height / 2 - 110, 0xFFFFFFFF);
        drawPreviewCover(graphics);
        graphics.drawCenteredString(font, Component.literal(preview == null ? "Waiting for local preview" : preview), width / 2, height / 2 - 40, 0xFFD0D0D0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    private void drawPreviewCover(GuiGraphics graphics) {
        MtvMediaMetadata metadata = model.preview();
        Identifier textureId = metadata == null ? null : MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        int x = width / 2 - 210, y = height / 2 - 45;
        graphics.fill(x, y, x + 64, y + 64, 0xFF333333);
        if (textureId != null) graphics.blit(RenderPipelines.GUI_TEXTURED, textureId, x, y, 0.0F, 0.0F, 64, 64, 64, 64, 64, 64, -1);
    }
    private String previewMessage(MtvMediaMetadata metadata) {
        String value;
        if (metadata == null) value = model.resolving() ? "Resolving locally..." : "Waiting for local preview";
        else if (metadata.status() == MtvMediaMetadata.Status.FAILED) value = "Local resolver: " + metadata.errorReason();
        else value = "Title: " + metadata.title() + " | Author: " + metadata.author() + " | Platform: " + metadata.platform() + " | Description: " + metadata.description() + " | Cover: " + MtvMediaCoverCache.getInstance().statusLabel(metadata.coverUrl());
        return model.lastError().name().equals("NONE") ? value : value + " | Server: " + model.lastError().name();
    }
}
