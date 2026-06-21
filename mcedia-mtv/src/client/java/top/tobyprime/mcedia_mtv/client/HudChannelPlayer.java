package top.tobyprime.mcedia_mtv.client;

import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_core.client.audio.SpeakerAudioChannelMode;
import top.tobyprime.mcedia_core.client.player.HudScreenPeripheral;
import top.tobyprime.mcedia_core.client.player.HudSpeakerPeripheral;
import top.tobyprime.mcedia_core.client.player.MediaPlayerHostManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelSession;

public final class HudChannelPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HudChannelPlayer.class);
    private static final HudChannelPlayer INSTANCE = new HudChannelPlayer();

    private @Nullable HudScreenPeripheral hudScreen;
    private @Nullable HudSpeakerPeripheral hudSpeakerLeft;
    private @Nullable HudSpeakerPeripheral hudSpeakerRight;
    private @Nullable String currentChannelId;
    private @Nullable ClientChannelSession channelSession;

    private HudChannelPlayer() {
    }

    public static HudChannelPlayer getInstance() {
        return INSTANCE;
    }

    public @Nullable String getCurrentChannelId() {
        return currentChannelId;
    }

    public void onBinding(@Nullable String channelId) {
        if (channelId == null || channelId.isBlank()) {
            unsubscribe();
            return;
        }
        if (channelId.equals(currentChannelId)) {
            return;
        }
        unsubscribe();
        subscribe(channelId);
    }

    private void subscribe(String channelId) {
        var mc = Minecraft.getInstance();

        var screen = new HudScreenPeripheral();
        initScreenSize(screen, mc);
        var speakerLeft = new HudSpeakerPeripheral();
        speakerLeft.setAudioChannelMode(SpeakerAudioChannelMode.LEFT);
        var speakerRight = new HudSpeakerPeripheral();
        speakerRight.setAudioChannelMode(SpeakerAudioChannelMode.RIGHT);

        var session = ClientChannelPlaybackManager.getInstance().attach(channelId);
        if (session == null) {
            closeSafely(screen);
            closeSafely(speakerLeft);
            closeSafely(speakerRight);
            LOGGER.warn("Failed to attach HUD channel session: {}", channelId);
            return;
        }

        var hostManager = MediaPlayerHostManager.get();
        var hostId = hostManager.getHostId(session.getHost());
        if (hostId == null) {
            closeSafely(screen);
            closeSafely(speakerLeft);
            closeSafely(speakerRight);
            ClientChannelPlaybackManager.getInstance().detach(channelId);
            LOGGER.warn("Failed to get host ID for HUD channel: {}", channelId);
            return;
        }

        hostManager.assignPeripheralToHost(hostId, screen);
        hostManager.assignPeripheralToHost(hostId, speakerLeft);
        hostManager.assignPeripheralToHost(hostId, speakerRight);

        this.hudScreen = screen;
        this.hudSpeakerLeft = speakerLeft;
        this.hudSpeakerRight = speakerRight;
        this.channelSession = session;
        this.currentChannelId = channelId;

        LOGGER.info("HUD channel player subscribed to {}", channelId);
    }

    public void unsubscribe() {
        if (currentChannelId == null) {
            return;
        }

        var channelId = currentChannelId;
        currentChannelId = null;

        if (channelSession != null) {
            var host = channelSession.getHost();

            if (hudScreen != null) {
                host.removePeripheral(hudScreen);
            }
            if (hudSpeakerLeft != null) {
                host.removePeripheral(hudSpeakerLeft);
            }
            if (hudSpeakerRight != null) {
                host.removePeripheral(hudSpeakerRight);
            }

            ClientChannelPlaybackManager.getInstance().detach(channelId);
        }

        closeSafely(hudScreen);
        closeSafely(hudSpeakerLeft);
        closeSafely(hudSpeakerRight);

        hudScreen = null;
        hudSpeakerLeft = null;
        hudSpeakerRight = null;
        channelSession = null;

        LOGGER.info("HUD channel player unsubscribed from {}", channelId);
    }

    public void cleanup() {
        unsubscribe();
    }

    private static void closeSafely(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to close peripheral", e);
        }
    }

    private static void initScreenSize(HudScreenPeripheral screen, Minecraft mc) {
        var window = mc.getWindow();
        int sw = window.getGuiScaledWidth();
        int sh = window.getGuiScaledHeight();
        screen.setScreenSize(sw / 3, sh / 3);
        screen.setPosition(sw - sw / 3, 0);
    }
}
