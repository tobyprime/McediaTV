package top.tobyprime.mcedia_mtv.client.entityplayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.MtvClientNetworkInitializer;

import java.util.HashMap;
import java.util.Map;

public final class EntityPlayerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityPlayerManager.class);
    private static final EntityPlayerManager INSTANCE = new EntityPlayerManager();

    public static final String PLAYER_TAG = "mcedia_mtv";
    public static final String LEGACY_PLAYER_TAG = "mcedia_interaction_player";
    public static final String CONFIG_KEY = "mcedia_mtv";
    public static final String LEGACY_CONFIG_KEY = "mcedia_interaction_player";
    public static final String ENTITY_CONFIG_KEY = "entity_config";

    private final Map<Display.ItemDisplay, EntityPlayerHandle> activePlayers = new HashMap<>();
    private ClientLevel currentLevel;

    public static EntityPlayerManager getInstance() {
        return INSTANCE;
    }

    private EntityPlayerManager() {
    }

    public void onInitialize() {
        LOGGER.info("EntityPlayerManager initialized");
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearAll();
            MtvClientNetworkInitializer.clearAll();
            currentLevel = null;
        });
    }

    private void onClientTick(Minecraft client) {
        var level = client.level;
        if (level != currentLevel) {
            clearAll();
            currentLevel = level;
            return;
        }
        if (level == null) return;

        LOGGER.debug("EntityPlayerManager tick: tracked={}, level={}", activePlayers.size(), level.dimension());
        scanForPlayers(level);
        ClientChannelPlaybackManager.getInstance().onClientTick(client);

        var iterator = activePlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var display = entry.getKey();
            var handle = entry.getValue();

            if (display.isRemoved()) {
                handle.destroy();
                iterator.remove();
                continue;
            }

            if (!hasPlayerConfig(display)) {
                handle.destroy();
                iterator.remove();
                continue;
            }

            handle.tick();
        }
    }

    private void scanForPlayers(ClientLevel level) {
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof Display.ItemDisplay display)) {
                continue;
            }
            if (display.isRemoved()) {
                continue;
            }
            if (!hasPlayerConfig(display)) {
                continue;
            }
            if (activePlayers.containsKey(display)) {
                continue;
            }

            LOGGER.debug("Found item display player: id={}, pos={}", display.getId(), display.position());
            var handle = new EntityPlayerHandle(display);
            activePlayers.put(display, handle);
        }
    }

    private static boolean hasPlayerConfig(Display.ItemDisplay display) {
        var itemStack = display.getSlot(0).get();
        if (itemStack.isEmpty()) {
            return false;
        }
        var customData = itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return false;
        }
        var tag = customData.copyTag();
        return tag.contains(ENTITY_CONFIG_KEY) || tag.contains(CONFIG_KEY) || tag.contains(LEGACY_CONFIG_KEY);
    }

    private void clearAll() {
        for (var handle : activePlayers.values()) {
            handle.destroy();
        }
        activePlayers.clear();
        LOGGER.info("Cleared all item display players");
    }
}
