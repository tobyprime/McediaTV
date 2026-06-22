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
    private static final long HIDE_DELAY_MS = 3_000L;

    private @Nullable HudScreenPeripheral hudScreen;
    private @Nullable HudSpeakerPeripheral hudSpeakerLeft;
    private @Nullable HudSpeakerPeripheral hudSpeakerRight;
    private @Nullable String currentChannelId;
    private @Nullable ClientChannelSession channelSession;
    private @Nullable Integer cachedHostId;

    /** 0 = playing, >0 = counting down to hide, <0 = already hidden */
    private long nonPlayingSinceMs;

    private boolean screenEnabled;
    private int screenX;
    private int screenY;
    private int screenWidth;
    private int screenHeight;

    private HudChannelPlayer() {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        screenWidth = window.getGuiScaledWidth() / 3;
        screenHeight = window.getGuiScaledHeight() / 3;
        screenX = window.getGuiScaledWidth() - screenWidth;
        screenY = 0;
    }

    public static HudChannelPlayer getInstance() {
        return INSTANCE;
    }

    public @Nullable String getCurrentChannelId() { return currentChannelId; }
    public boolean isScreenEnabled() { return screenEnabled; }
    public int getScreenX() { return screenX; }
    public int getScreenY() { return screenY; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public void setScreenEnabled(boolean enabled) {
        if (screenEnabled == enabled) return;
        screenEnabled = enabled;
        if (channelSession != null) {
            if (enabled) {
                attachScreen();
            } else {
                detachScreen();
            }
        }
        LOGGER.info("HUD screen {}", enabled ? "enabled" : "disabled");
    }

    public void setScreenPosition(int x, int y) {
        this.screenX = x;
        this.screenY = y;
        if (hudScreen != null) {
            hudScreen.setPosition(x, y);
        }
    }

    public void setScreenSize(int width, int height) {
        this.screenWidth = Math.max(width, 1);
        this.screenHeight = Math.max(height, 1);
        if (hudScreen != null) {
            hudScreen.setScreenSize(this.screenWidth, this.screenHeight);
        }
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

    public void onClientTick() {
        if (channelSession == null || currentChannelId == null || hudScreen == null) {
            return;
        }
        var snapshot = channelSession.getSnapshot();
        if (snapshot != null && snapshot.isPlaying()) {
            if (nonPlayingSinceMs < 0) {
                setScreenOnHost(true);
                LOGGER.debug("HUD shown: channel {} started playing", currentChannelId);
            }
            nonPlayingSinceMs = 0;
        } else {
            if (nonPlayingSinceMs == 0) {
                nonPlayingSinceMs = System.currentTimeMillis();
            } else if (nonPlayingSinceMs > 0
                    && System.currentTimeMillis() - nonPlayingSinceMs >= HIDE_DELAY_MS) {
                nonPlayingSinceMs = -1;
                setScreenOnHost(false);
                LOGGER.debug("HUD hidden: channel {} not playing for {}ms", currentChannelId, HIDE_DELAY_MS);
            }
        }
    }

    private void subscribe(String channelId) {
        var speakerLeft = new HudSpeakerPeripheral();
        speakerLeft.setAudioChannelMode(SpeakerAudioChannelMode.LEFT);
        var speakerRight = new HudSpeakerPeripheral();
        speakerRight.setAudioChannelMode(SpeakerAudioChannelMode.RIGHT);

        var session = ClientChannelPlaybackManager.getInstance().attach(channelId);
        if (session == null) {
            closeSafely(speakerLeft);
            closeSafely(speakerRight);
            LOGGER.warn("Failed to attach HUD channel session: {}", channelId);
            return;
        }

        var hostManager = MediaPlayerHostManager.get();
        var resolvedHostId = hostManager.getHostId(session.getHost());
        if (resolvedHostId == null) {
            closeSafely(speakerLeft);
            closeSafely(speakerRight);
            ClientChannelPlaybackManager.getInstance().detach(channelId);
            LOGGER.warn("Failed to get host ID for HUD channel: {}", channelId);
            return;
        }

        hostManager.assignPeripheralToHost(resolvedHostId, speakerLeft);
        hostManager.assignPeripheralToHost(resolvedHostId, speakerRight);

        if (screenEnabled) {
            var screen = createScreen();
            hostManager.assignPeripheralToHost(resolvedHostId, screen);
            this.hudScreen = screen;
        }

        this.cachedHostId = resolvedHostId;
        this.hudSpeakerLeft = speakerLeft;
        this.hudSpeakerRight = speakerRight;
        this.channelSession = session;
        this.currentChannelId = channelId;
        this.nonPlayingSinceMs = 0;

        LOGGER.info("HUD channel player subscribed to {}", channelId);
    }

    public void unsubscribe() {
        if (currentChannelId == null) return;

        var channelId = currentChannelId;
        currentChannelId = null;

        if (channelSession != null) {
            var host = channelSession.getHost();
            if (hudScreen != null) host.removePeripheral(hudScreen);
            if (hudSpeakerLeft != null) host.removePeripheral(hudSpeakerLeft);
            if (hudSpeakerRight != null) host.removePeripheral(hudSpeakerRight);
            ClientChannelPlaybackManager.getInstance().detach(channelId);
        }

        closeSafely(hudScreen);
        closeSafely(hudSpeakerLeft);
        closeSafely(hudSpeakerRight);

        hudScreen = null;
        hudSpeakerLeft = null;
        hudSpeakerRight = null;
        channelSession = null;
        cachedHostId = null;
        nonPlayingSinceMs = 0;

        LOGGER.info("HUD channel player unsubscribed from {}", channelId);
    }

    public void cleanup() {
        unsubscribe();
    }

    private void attachScreen() {
        if (channelSession == null || hudScreen != null) return;
        var screen = createScreen();
        if (cachedHostId == null) {
            closeSafely(screen);
            return;
        }
        MediaPlayerHostManager.get().assignPeripheralToHost(cachedHostId, screen);
        this.hudScreen = screen;
    }

    private void detachScreen() {
        if (channelSession == null || hudScreen == null) return;
        channelSession.getHost().removePeripheral(hudScreen);
        closeSafely(hudScreen);
        hudScreen = null;
        nonPlayingSinceMs = 0;
    }

    private void setScreenOnHost(boolean attached) {
        if (channelSession == null || hudScreen == null) return;
        if (attached) {
            if (cachedHostId != null) {
                MediaPlayerHostManager.get().assignPeripheralToHost(cachedHostId, hudScreen);
            }
        } else {
            channelSession.getHost().removePeripheral(hudScreen);
        }
    }

    private HudScreenPeripheral createScreen() {
        var screen = new HudScreenPeripheral();
        screen.setScreenSize(screenWidth, screenHeight);
        screen.setPosition(screenX, screenY);
        return screen;
    }

    private static void closeSafely(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to close peripheral", e);
        }
    }
}
