package top.tobyprime.mcedia_mtv.client.worldui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlSender;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;

/** Full-screen local URL preview and bounded playlist insertion controls. */
public final class MtvAddMediaScreen extends Screen {
    private final WorldUiInteractionState.Target target;
    private final WorldUiAddMediaModel model;
    private EditBox input;
    private StringWidget previewText;
    private Button prepend;
    private Button insertNext;
    private Button append;
    private Button playNow;

    private MtvAddMediaScreen(WorldUiInteractionState.Target target) {
        super(Component.literal("MTV / Add media"));
        this.target = target;
        this.model = new WorldUiAddMediaModel(top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache.getInstance());
    }

    public static void open(Minecraft client, WorldUiInteractionState.Target target) {
        if (target != null) client.setScreen(new MtvAddMediaScreen(target));
    }

    @Override
    protected void init() {
        int left = width / 2 - 220;
        input = addRenderableWidget(new EditBox(font, left, height / 2 - 80, 440, 24, Component.literal("URL")));
        input.setMaxLength(2048);
        input.setHint(Component.literal("Paste a supported media URL"));
        input.setResponder(model::setInput);
        previewText = addRenderableWidget(new StringWidget(left, height / 2 - 45, 440, 80, Component.literal("Waiting for local preview"), font));
        prepend = addButton(left, height / 2 + 50, "Prepend", WorldUiAddMediaModel.AddMode.PREPEND);
        insertNext = addButton(left + 112, height / 2 + 50, "Insert next", WorldUiAddMediaModel.AddMode.INSERT_NEXT);
        append = addButton(left + 224, height / 2 + 50, "Append", WorldUiAddMediaModel.AddMode.APPEND);
        playNow = addButton(left + 336, height / 2 + 50, "Play now", WorldUiAddMediaModel.AddMode.INSERT_AND_PLAY);
        input.setFocused(true);
    }

    private Button addButton(int x, int y, String label, WorldUiAddMediaModel.AddMode mode) {
        return addRenderableWidget(Button.builder(Component.literal(label), button -> model.confirm(target, WorldUiControlSender.getInstance(), mode)).bounds(x, y, 104, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        boolean enabled = model.canConfirm();
        if (prepend != null) { prepend.active = enabled; insertNext.active = enabled; append.active = enabled; playNow.active = enabled; }
        MtvMediaMetadata metadata = model.preview();
        model.onControlResult(WorldUiControlSender.getInstance().lastResult());
        previewText.setMessage(Component.literal(metadata == null ? (model.resolving() ? "Resolving locally..." : "Waiting for local preview") :
                "Title: " + metadata.title() + " | Author: " + metadata.author() + " | Platform: " + metadata.platform() +
                        " | Description: " + metadata.description() + " | Cover: " + (metadata.coverUrl().isBlank() ? "fallback" : "local cover")));
        if (model.consumeAccepted()) minecraft.setScreen(null);
    }
}
