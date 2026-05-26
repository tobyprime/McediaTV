package top.tobyprime.mcedia_mtv.client.entityplayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;

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

    private record ConfigProbe(boolean emptyItemStack, boolean missingCustomData, boolean missingConfigCompound) {
    }

    public static EntityPlayerManager getInstance() {
        return INSTANCE;
    }

    private EntityPlayerManager() {
    }

    public void onInitialize() {
        LOGGER.debug("EntityPlayerManager initialized");
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearAll();
            ClientChannelPlaybackManager.getInstance().clear();
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

        scanForPlayers(level);
        ClientChannelPlaybackManager.getInstance().onClientTick(client);

        var iterator = activePlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var display = entry.getKey();
            var handle = entry.getValue();

            if (display.isRemoved()) {
                LOGGER.info("Destroy MTV handle because host display was removed: entityId={}, uuid={}, removalReason={}", display.getId(), display.getUUID(), display.getRemovalReason());
                handle.destroy("host display removed");
                iterator.remove();
                continue;
            }

            if (!hasPlayerConfig(display)) {
                var probe = probeConfig(display);
                LOGGER.info(
                        "Destroy MTV handle because host display config is unavailable: entityId={}, uuid={}, emptyItemStack={}, missingCustomData={}, missingConfigCompound={}",
                        display.getId(),
                        display.getUUID(),
                        probe.emptyItemStack(),
                        probe.missingCustomData(),
                        probe.missingConfigCompound()
                );
                handle.destroy("host display config unavailable");
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

            LOGGER.info("Track MTV host display: entityId={}, uuid={}, pos={}", display.getId(), display.getUUID(), display.position());
            var handle = new EntityPlayerHandle(display);
            activePlayers.put(display, handle);
        }
    }

    private static boolean hasPlayerConfig(Display.ItemDisplay display) {
        var probe = probeConfig(display);
        return !probe.emptyItemStack() && !probe.missingCustomData() && !probe.missingConfigCompound();
    }

    private static ConfigProbe probeConfig(Display.ItemDisplay display) {
        var itemStack = display.getSlot(0).get();
        if (itemStack.isEmpty()) {
            return new ConfigProbe(true, true, true);
        }
        var customData = itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return new ConfigProbe(false, true, true);
        }
        var tag = customData.copyTag();
        boolean hasConfigCompound = tag.contains(ENTITY_CONFIG_KEY) || tag.contains(CONFIG_KEY) || tag.contains(LEGACY_CONFIG_KEY);
        return new ConfigProbe(false, false, !hasConfigCompound);
    }

    private void clearAll() {
        for (var handle : activePlayers.values()) {
            handle.destroy("manager clearAll");
        }
        activePlayers.clear();
        LOGGER.debug("Cleared all item display players");
    }
}
