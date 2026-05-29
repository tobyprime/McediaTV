package top.tobyprime.mcedia_mtv.client.entityplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display.ItemDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_core.client.audio.SpeakerAudioChannelMode;
import top.tobyprime.mcedia_core.client.player.MediaPlayerHostManager;
import top.tobyprime.mcedia_core.client.player.ScreenPeripheral;
import top.tobyprime.mcedia_core.client.player.ScreenPeripheral.ScreenFillMode;
import top.tobyprime.mcedia_core.client.player.SpeakerPeripheral;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelSession;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityPlayerHandle {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityPlayerHandle.class);
    private static final Identifier DEFAULT_BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private static final float DEFAULT_SCREEN_WIDTH = 1.6F;
    private static final float DEFAULT_SCREEN_HEIGHT = 0.9F;

    private final ItemDisplay display;
    private final Map<String, RuntimePeripheralHandle> runtimePeripherals = new LinkedHashMap<>();

    private @Nullable String channelId;
    private @Nullable ClientChannelSession channelSession;
    private boolean missingHostConfigLogged;

    public EntityPlayerHandle(ItemDisplay display) {
        this.display = display;
    }

    public void tick() {
        try {
            var config = readConfig();
            syncChannelSession(config.channelId());
            syncPeripherals(config.masterVolume(), config.peripherals());
        } catch (Exception e) {
            LOGGER.warn("Error ticking item display player id={}", display.getId(), e);
        }
    }

    private void syncChannelSession(@Nullable String desiredChannelId) {
        if ((channelId == null && desiredChannelId == null) || (channelId != null && channelId.equals(desiredChannelId))) {
            return;
        }
        if (channelId != null) {
            ClientChannelPlaybackManager.getInstance().detach(channelId);
        }
        channelId = desiredChannelId;
        channelSession = ClientChannelPlaybackManager.getInstance().attach(desiredChannelId);
        reattachPeripherals();
    }

    private void syncPeripherals(float masterVolume, List<PeripheralConfig> desiredPeripherals) {
        if (desiredPeripherals.isEmpty() && !runtimePeripherals.isEmpty()) {
            LOGGER.info(
                    "MTV host config resolved to empty peripheral list while runtimes still exist: entityId={}, uuid={}, runtimeCount={}, channelId={}",
                    display.getId(),
                    display.getUUID(),
                    runtimePeripherals.size(),
                    channelId
            );
        }

        var nextIds = new LinkedHashSet<String>();
        for (var peripheral : desiredPeripherals) {
            String id = peripheral.id();
            nextIds.add(id);

            var runtime = runtimePeripherals.get(id);
            boolean created = false;
            if (runtime == null || runtime.kind() != peripheral.kind()) {
                if (runtime != null) {
                    destroyRuntimePeripheral(runtime);
                    runtimePeripherals.remove(id);
                }
                runtime = createRuntimePeripheral(peripheral);
                if (runtime == null) {
                    continue;
                }
                runtimePeripherals.put(id, runtime);
                created = true;
            }

            applyPeripheralConfig(runtime, peripheral, masterVolume);
            if (created) {
                assignRuntimePeripheral(runtime);
            }
        }

        var staleIds = new LinkedHashSet<>(runtimePeripherals.keySet());
        staleIds.removeAll(nextIds);
        for (var id : staleIds) {
            var runtime = runtimePeripherals.remove(id);
            if (runtime != null) {
                destroyRuntimePeripheral(runtime);
            }
        }
    }

    private void reattachPeripherals() {
        for (var runtime : runtimePeripherals.values()) {
            assignRuntimePeripheral(runtime);
        }
    }

    private void assignRuntimePeripheral(RuntimePeripheralHandle runtime) {
        if (channelSession == null) {
            return;
        }
        var hostId = MediaPlayerHostManager.get().getHostId(channelSession.getHost());
        if (hostId == null) {
            return;
        }
        switch (runtime) {
            case ScreenRuntimeHandle screenRuntime -> MediaPlayerHostManager.get().assignPeripheralToHost(hostId, screenRuntime.screen());
            case SpeakerRuntimeHandle speakerRuntime -> MediaPlayerHostManager.get().assignPeripheralToHost(hostId, speakerRuntime.speaker());
        }
    }

    private @Nullable RuntimePeripheralHandle createRuntimePeripheral(PeripheralConfig config) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        return switch (config.kind()) {
            case SCREEN -> {
                var screen = new ScreenPeripheral(level);
                LOGGER.info("Create MTV screen runtime: hostEntityId={}, hostUuid={}, runtimeId={}", display.getId(), display.getUUID(), config.id());
                yield new ScreenRuntimeHandle(config.id(), screen);
            }
            case SPEAKER -> {
                var speaker = new SpeakerPeripheral(level);
                speaker.setMaxRange(SpeakerPeripheral.DEFAULT_MAX_RANGE);
                LOGGER.info("Create MTV speaker runtime: hostEntityId={}, hostUuid={}, runtimeId={}", display.getId(), display.getUUID(), config.id());
                yield new SpeakerRuntimeHandle(config.id(), speaker);
            }
        };
    }

    private void applyPeripheralConfig(RuntimePeripheralHandle runtime, PeripheralConfig peripheral, float masterVolume) {
        switch (runtime) {
            case ScreenRuntimeHandle screenRuntime when peripheral instanceof ScreenPeripheralConfig screenConfig ->
                    applyScreenConfig(screenRuntime.screen(), screenConfig);
            case SpeakerRuntimeHandle speakerRuntime when peripheral instanceof SpeakerPeripheralConfig speakerConfig ->
                    applySpeakerConfig(speakerRuntime.speaker(), speakerConfig, masterVolume);
            default -> {
            }
        }
    }

    private void applyScreenConfig(ScreenPeripheral screen, ScreenPeripheralConfig config) {
        var transform = computeTransform(config);
        screen.setPos(transform.position().x(), transform.position().y(), transform.position().z());
        screen.setWorldRotation(transform.rotation());
        screen.setMinBrightness(config.minBrightness());
        screen.setFillMode(config.fillMode());
        screen.setBackgroundTextureId(config.backgroundTextureId());
        screen.setDanmakuVisible(config.danmakuVisible());

        float width = config.width() > 0.0F ? config.width() : DEFAULT_SCREEN_WIDTH;
        float height = config.height() > 0.0F ? config.height() : DEFAULT_SCREEN_HEIGHT;
        screen.setScreenSize(width, height);
    }

    private void applySpeakerConfig(SpeakerPeripheral speaker, SpeakerPeripheralConfig config, float masterVolume) {
        var transform = computeTransform(config);
        speaker.setPos(transform.position().x(), transform.position().y(), transform.position().z());
        speaker.setMaxRange(config.maxRange());
        speaker.setVolume(config.volume() * masterVolume);
        speaker.setAudioChannelMode(parseChannelMode(config.channelMode()));
    }

    private static SpeakerAudioChannelMode parseChannelMode(String mode) {
        return switch (mode) {
            case "left" -> SpeakerAudioChannelMode.LEFT;
            case "right" -> SpeakerAudioChannelMode.RIGHT;
            default -> SpeakerAudioChannelMode.MIX;
        };
    }

    private TransformState computeTransform(PeripheralConfig config) {
        var hostRotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-display.getYRot()))
                .rotateX((float) Math.toRadians(-display.getXRot()));
        var finalRotation = new Quaternionf(hostRotation);

        if (config.offsetRx() != 0.0F || config.offsetRy() != 0.0F || config.offsetRz() != 0.0F || config.offsetRw() != 0.0F) {
            var rotOffset = new Quaternionf(config.offsetRx(), config.offsetRy(), config.offsetRz(), config.offsetRw()).normalize();
            finalRotation.mul(rotOffset);
        }

        var localOffset = new Vector3f(config.offsetX(), config.offsetY(), config.offsetZ());
        if (localOffset.x() != 0.0F || localOffset.y() != 0.0F || localOffset.z() != 0.0F) {
            hostRotation.transform(localOffset);
        }

        var pos = display.position();
        return new TransformState(
                new Vector3f(
                        (float) pos.x() + localOffset.x(),
                        (float) pos.y() + localOffset.y(),
                        (float) pos.z() + localOffset.z()
                ),
                finalRotation
        );
    }

    private HostConfig readConfig() {
        var itemStack = display.getSlot(0).get();
        if (itemStack.isEmpty()) {
            return HostConfig.DEFAULT;
        }

        var customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            if (!missingHostConfigLogged) {
                LOGGER.info("MTV host display custom data became empty: entityId={}, uuid={}", display.getId(), display.getUUID());
                missingHostConfigLogged = true;
            }
            return HostConfig.DEFAULT;
        }

        var tag = customData.copyTag();
        LOGGER.debug("Item display {} custom data root: {}", display.getId(), tag);
        var configTag = tag.getCompoundOrEmpty(EntityPlayerManager.ENTITY_CONFIG_KEY);
        if (configTag.isEmpty()) {
            configTag = tag.getCompoundOrEmpty(EntityPlayerManager.CONFIG_KEY);
        }
        if (configTag.isEmpty()) {
            configTag = tag.getCompoundOrEmpty(EntityPlayerManager.LEGACY_CONFIG_KEY);
        }
        if (configTag.isEmpty()) {
            if (!missingHostConfigLogged) {
                LOGGER.info("MTV host display lost player config compound: entityId={}, uuid={}", display.getId(), display.getUUID());
                missingHostConfigLogged = true;
            }
            return HostConfig.DEFAULT;
        }

        if (missingHostConfigLogged) {
            LOGGER.info("MTV host display config recovered: entityId={}, uuid={}", display.getId(), display.getUUID());
            missingHostConfigLogged = false;
        }

        String boundChannelId = null;
        var bindingTag = tag.getCompoundOrEmpty("channel_binding");
        if (!bindingTag.isEmpty()) {
            String bindingType = bindingTag.getStringOr("type", "self");
            if ("broadcast".equalsIgnoreCase(bindingType)) {
                boundChannelId = bindingTag.getStringOr("channel_id", "");
                if (boundChannelId != null && boundChannelId.isBlank()) {
                    boundChannelId = null;
                }
            } else if ("self".equalsIgnoreCase(bindingType)) {
                boundChannelId = "self:" + display.getUUID();
            } else {
                boundChannelId = bindingTag.getStringOr("channel_id", "");
                if (boundChannelId != null && boundChannelId.isBlank()) {
                    boundChannelId = null;
                }
            }
        }
        if (boundChannelId == null) {
            boundChannelId = "self:" + display.getUUID();
        }

        boolean powered = configTag.getBooleanOr("powered", true);
        if (!powered) {
            boundChannelId = null;
        }

        var peripherals = readPeripheralList(configTag.getListOrEmpty("peripherals"));
        float masterVolume = Math.max(0.0F, Math.min(1.0F, configTag.getFloatOr("master_volume", 1.0F)));
        return new HostConfig(boundChannelId, masterVolume, peripherals);
    }

    private List<PeripheralConfig> readPeripheralList(ListTag peripheralsTag) {
        var result = new java.util.ArrayList<PeripheralConfig>();
        var usedIds = new LinkedHashSet<String>();

        for (int i = 0; i < peripheralsTag.size(); i++) {
            var peripheralTag = peripheralsTag.getCompoundOrEmpty(i);
            var peripheral = readPeripheral(peripheralTag, i, usedIds);
            if (peripheral != null) {
                result.add(peripheral);
            }
        }

        return List.copyOf(result);
    }

    private @Nullable PeripheralConfig readPeripheral(CompoundTag peripheralTag, int index, Set<String> usedIds) {
        String type = peripheralTag.getStringOr("type", "");
        PeripheralKind kind = PeripheralKind.fromString(type);
        if (kind == null) {
            LOGGER.warn("Ignore item display player peripheral with unknown type: {}", type);
            return null;
        }

        String id = peripheralTag.getStringOr("id", "");
        if (id.isBlank()) {
            id = kind.idPrefix() + "_" + index;
        }
        if (!usedIds.add(id)) {
            LOGGER.warn("Ignore duplicate item display player peripheral id: {}", id);
            return null;
        }

        float offsetX = peripheralTag.getFloatOr("offset_x", 0.0F);
        float offsetY = peripheralTag.getFloatOr("offset_y", 0.0F);
        float offsetZ = peripheralTag.getFloatOr("offset_z", 0.0F);
        float offsetRx = peripheralTag.getFloatOr("offset_rx", 0.0F);
        float offsetRy = peripheralTag.getFloatOr("offset_ry", 0.0F);
        float offsetRz = peripheralTag.getFloatOr("offset_rz", 0.0F);
        float offsetRw = peripheralTag.getFloatOr("offset_rw", 1.0F);

        return switch (kind) {
            case SCREEN -> new ScreenPeripheralConfig(
                    id,
                    offsetX,
                    offsetY,
                    offsetZ,
                    offsetRx,
                    offsetRy,
                    offsetRz,
                    offsetRw,
                    peripheralTag.getIntOr("min_brightness", 8),
                    peripheralTag.getFloatOr("width", 0.0F),
                    peripheralTag.getFloatOr("height", 0.0F),
                    parseFillMode(peripheralTag.getStringOr("fill_mode", "keep_aspect_cover")),
                    parseBackgroundTexture(peripheralTag.getStringOr("background_texture", "")),
                    peripheralTag.getBooleanOr("danmaku_visible", true)
            );
            case SPEAKER -> new SpeakerPeripheralConfig(
                    id,
                    offsetX,
                    offsetY,
                    offsetZ,
                    offsetRx,
                    offsetRy,
                    offsetRz,
                    offsetRw,
                    peripheralTag.getFloatOr("max_range", SpeakerPeripheral.DEFAULT_MAX_RANGE),
                    peripheralTag.getFloatOr("volume", 1.0F),
                    peripheralTag.getStringOr("channel_mode", "mix")
            );
        };
    }

    private static ScreenFillMode parseFillMode(String value) {
        return switch (value) {
            case "fill", "FILL" -> ScreenFillMode.FILL;
            default -> ScreenFillMode.KEEP_ASPECT_COVER;
        };
    }

    private static @Nullable Identifier parseBackgroundTexture(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BACKGROUND_TEXTURE;
        }
        var id = Identifier.tryParse(value);
        return id != null ? id : DEFAULT_BACKGROUND_TEXTURE;
    }

    public void destroy(String reason) {
        LOGGER.info(
                "Destroy MTV handle: entityId={}, uuid={}, reason={}, runtimeCount={}, channelId={}",
                display.getId(),
                display.getUUID(),
                reason,
                runtimePeripherals.size(),
                channelId
        );
        if (channelId != null) {
            ClientChannelPlaybackManager.getInstance().detach(channelId);
        }
        channelSession = null;
        channelId = null;
        for (var runtime : runtimePeripherals.values()) {
            destroyRuntimePeripheral(runtime);
        }
        runtimePeripherals.clear();
    }

    public @Nullable String getChannelId() {
        return channelId;
    }

    private void destroyRuntimePeripheral(RuntimePeripheralHandle runtime) {
        switch (runtime) {
            case ScreenRuntimeHandle screenRuntime -> {
                LOGGER.info(
                        "Destroy MTV screen runtime: hostEntityId={}, hostUuid={}, runtimeId={}",
                        display.getId(),
                        display.getUUID(),
                        runtime.id()
                );
                screenRuntime.screen().close();
            }
            case SpeakerRuntimeHandle speakerRuntime -> {
                LOGGER.info(
                        "Destroy MTV speaker runtime: hostEntityId={}, hostUuid={}, runtimeId={}",
                        display.getId(),
                        display.getUUID(),
                        runtime.id()
                );
                speakerRuntime.speaker().close();
            }
        }
    }

    private record HostConfig(
            @Nullable String channelId,
            float masterVolume,
            List<PeripheralConfig> peripherals
    ) {
        private static final HostConfig DEFAULT = new HostConfig(null, 1.0F, List.of());
    }

    private sealed interface PeripheralConfig permits ScreenPeripheralConfig, SpeakerPeripheralConfig {
        String id();

        PeripheralKind kind();

        float offsetX();

        float offsetY();

        float offsetZ();

        float offsetRx();

        float offsetRy();

        float offsetRz();

        float offsetRw();
    }

    private record ScreenPeripheralConfig(
            String id,
            float offsetX,
            float offsetY,
            float offsetZ,
            float offsetRx,
            float offsetRy,
            float offsetRz,
            float offsetRw,
            int minBrightness,
            float width,
            float height,
            ScreenFillMode fillMode,
            @Nullable Identifier backgroundTextureId,
            boolean danmakuVisible
    ) implements PeripheralConfig {
        @Override
        public PeripheralKind kind() {
            return PeripheralKind.SCREEN;
        }
    }

    private record SpeakerPeripheralConfig(
            String id,
            float offsetX,
            float offsetY,
            float offsetZ,
            float offsetRx,
            float offsetRy,
            float offsetRz,
            float offsetRw,
            float maxRange,
            float volume,
            String channelMode
    ) implements PeripheralConfig {
        @Override
        public PeripheralKind kind() {
            return PeripheralKind.SPEAKER;
        }
    }

    private enum PeripheralKind {
        SCREEN("screen"),
        SPEAKER("speaker");

        private final String idPrefix;

        PeripheralKind(String idPrefix) {
            this.idPrefix = idPrefix;
        }

        public String idPrefix() {
            return idPrefix;
        }

        public static @Nullable PeripheralKind fromString(String value) {
            return switch (value) {
                case "screen" -> SCREEN;
                case "speaker" -> SPEAKER;
                default -> null;
            };
        }
    }

    private sealed interface RuntimePeripheralHandle permits ScreenRuntimeHandle, SpeakerRuntimeHandle {
        String id();

        PeripheralKind kind();
    }

    private record ScreenRuntimeHandle(String id, ScreenPeripheral screen) implements RuntimePeripheralHandle {
        @Override
        public PeripheralKind kind() {
            return PeripheralKind.SCREEN;
        }
    }

    private record SpeakerRuntimeHandle(String id, SpeakerPeripheral speaker) implements RuntimePeripheralHandle {
        @Override
        public PeripheralKind kind() {
            return PeripheralKind.SPEAKER;
        }
    }

    private record TransformState(Vector3f position, Quaternionf rotation) {
    }
}
