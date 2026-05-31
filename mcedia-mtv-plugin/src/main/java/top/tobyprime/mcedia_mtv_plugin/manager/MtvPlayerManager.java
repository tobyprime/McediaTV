package top.tobyprime.mcedia_mtv_plugin.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelService;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.model.ScreenPeripheralConfigModel;
import top.tobyprime.mcedia_mtv_plugin.model.SpeakerPeripheralConfigModel;
import top.tobyprime.mcedia_mtv_plugin.util.InteractionDataCommandBridge;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class MtvPlayerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvPlayerManager.class);

    private final JavaPlugin plugin;
    private final MtvChannelService channelService;

    public MtvPlayerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.channelService = new MtvChannelService(this);
    }

    public MtvChannelService getChannelService() {
        return channelService;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }


    public void readSnapshot(UUID uuid, Consumer<ManagedMtvPlayer> done) {
        withDisplay(uuid, display -> readFromEntity(display), done);
    }

    public ManagedMtvPlayer readFromEntity(ItemDisplay display) {
        var player = new ManagedMtvPlayer();
        player.setUuid(display.getUniqueId());
        player.setName(extractName(display));
        player.captureLocation(display.getLocation());
        var tag = readCustomData(display);
        readConfig(tag, display, player);
        return player;
    }

    private String extractName(ItemDisplay display) {
        var customName = display.customName();
        if (customName != null) {
            String text = PlainTextComponentSerializer.plainText().serialize(customName);
            if (text.startsWith("mtv:")) return text.substring(4);
            return text;
        }
        return "mtv-" + display.getEntityId();
    }

    private void readConfig(CompoundTag tag, ItemDisplay display, ManagedMtvPlayer player) {
        if (tag == null || tag.isEmpty()) return;

        var entityConfig = tag.getCompoundOrEmpty(InteractionDataCommandBridge.ENTITY_CONFIG_KEY);
        if (!entityConfig.isEmpty()) {
            player.setName(entityConfig.getStringOr("name", player.getName()));
            player.setWorld(entityConfig.getStringOr("world", player.getWorld()));
            player.setX(entityConfig.getDoubleOr("x", player.getX()));
            player.setY(entityConfig.getDoubleOr("y", player.getY()));
            player.setZ(entityConfig.getDoubleOr("z", player.getZ()));
            player.setYaw(entityConfig.getFloatOr("yaw", player.getYaw()));
            player.setPitch(entityConfig.getFloatOr("pitch", player.getPitch()));
            player.setMasterVolume(entityConfig.getFloatOr("master_volume", player.getMasterVolume()));
            player.setMaxActiveRange(entityConfig.getFloatOr("max_active_range", player.getMaxActiveRange()));
            player.setPowered(entityConfig.getBooleanOr("powered", player.isPowered()));
            String ownerStr = entityConfig.getStringOr("owner", "");
            if (!ownerStr.isBlank()) {
                try {
                    player.setOwner(UUID.fromString(ownerStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
            player.setPublic(entityConfig.getBooleanOr("is_public", player.isPublic()));

            var peripherals = entityConfig.getListOrEmpty("peripherals");
            for (int i = 0; i < peripherals.size(); i++) {
                var pt = peripherals.getCompoundOrEmpty(i);
                String type = pt.getStringOr("type", "");
                if ("screen".equals(type)) {
                    var s = new ScreenPeripheralConfigModel(pt.getStringOr("id", "screen_" + countScreenType(peripherals, i)));
                    s.setOffsetX(pt.getFloatOr("offset_x", 0));
                    s.setOffsetY(pt.getFloatOr("offset_y", 0.5F));
                    s.setOffsetZ(pt.getFloatOr("offset_z", 0));
                    s.setOffsetRx(pt.getFloatOr("offset_rx", 0));
                    s.setOffsetRy(pt.getFloatOr("offset_ry", 0));
                    s.setOffsetRz(pt.getFloatOr("offset_rz", 0));
                    s.setOffsetRw(pt.getFloatOr("offset_rw", 1));
                    s.setMinBrightness(pt.getIntOr("min_brightness", 8));
                    s.setWidth(pt.getFloatOr("width", 2));
                    s.setHeight(pt.getFloatOr("height", 1.125F));
                    s.setFillMode(pt.getStringOr("fill_mode", "keep_aspect_cover"));
                    s.setBackgroundTexture(pt.getStringOr("background_texture", "mcedia:textures/gui/idle_screen.png"));
                    s.setDanmakuVisible(pt.getBooleanOr("danmaku_visible", true));
                    player.getScreens().add(s);
                } else if ("speaker".equals(type)) {
                    var s = new SpeakerPeripheralConfigModel(pt.getStringOr("id", "speaker_" + countSpeakerType(peripherals, i)));
                    s.setOffsetX(pt.getFloatOr("offset_x", 0));
                    s.setOffsetY(pt.getFloatOr("offset_y", 0));
                    s.setOffsetZ(pt.getFloatOr("offset_z", 0));
                    s.setOffsetRx(pt.getFloatOr("offset_rx", 0));
                    s.setOffsetRy(pt.getFloatOr("offset_ry", 0));
                    s.setOffsetRz(pt.getFloatOr("offset_rz", 0));
                    s.setOffsetRw(pt.getFloatOr("offset_rw", 1));
                    s.setMaxRange(pt.getFloatOr("max_range", 16));
                    s.setVolume(pt.getFloatOr("volume", 1));
                    s.setChannelMode(pt.getStringOr("channel_mode", "mix"));
                    player.getSpeakers().add(s);
                }
            }
        }

        var bindingTag = tag.getCompoundOrEmpty(InteractionDataCommandBridge.CHANNEL_BINDING_KEY);
        if (!bindingTag.isEmpty()) {
            String type = bindingTag.getStringOr("type", "self");
            String channelId = bindingTag.getStringOr("channel_id", "");
            String regionKey = bindingTag.getStringOr("region_key", "");
            if ("broadcast".equalsIgnoreCase(type) && !channelId.isBlank()) {
                player.setChannelBinding(new MtvChannelBinding(top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelType.BROADCAST, channelId, regionKey));
            } else if ("self".equalsIgnoreCase(type)) {
                player.setChannelBinding(new MtvChannelBinding(top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelType.SELF, "", regionKey));
            }
        }
        if (player.getChannelBinding() == null) {
            player.setChannelBinding(MtvChannelBinding.self());
        }
    }

    private CompoundTag readCustomData(ItemDisplay display) {
        var stack = display.getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        var nmsStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack);
        var customData = nmsStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }
        return customData.copyTag();
    }

    private static int countScreenType(ListTag list, int upTo) {
        int c = 0;
        for (int i = 0; i < upTo; i++) if ("screen".equals(list.getCompoundOrEmpty(i).getStringOr("type", ""))) c++;
        return c;
    }

    private static int countSpeakerType(ListTag list, int upTo) {
        int c = 0;
        for (int i = 0; i < upTo; i++) if ("speaker".equals(list.getCompoundOrEmpty(i).getStringOr("type", ""))) c++;
        return c;
    }

    public void createPlayerAsync(Location location, String name, Player creator, Consumer<ManagedMtvPlayer> done) {
        if (location.getWorld() == null) {
            done.accept(null);
            return;
        }
        plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
            ItemDisplay itemDisplay = spawnItemDisplay(location);
            var player = ManagedMtvPlayer.create(itemDisplay.getUniqueId(), name, location);
            if (creator != null) {
                player.setOwner(creator.getUniqueId());
            }
            player.setPublic(false);
            // Create the self channel state before exposing the binding to clients.
            channelService.createSelfChannelState(itemDisplay.getUniqueId());
            applyEntityState(itemDisplay, player);
            done.accept(player);
        });
    }

    public void deletePlayerAsync(UUID uuid, Consumer<Boolean> done) {
        channelService.unregister(uuid);
        withDisplay(uuid, display -> {
            display.remove();
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    private void mutate(UUID uuid, Function<ManagedMtvPlayer, Boolean> mutation, Consumer<Boolean> done) {
        withDisplay(uuid, display -> {
            var player = readFromEntity(display);
            if (!Boolean.TRUE.equals(mutation.apply(player))) {
                return Boolean.FALSE;
            }
            applyEntityState(display, player);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public void updateScreenSize(UUID uuid, String periphId, float dw, float dh, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setWidth(Math.max(0.25F, s.getWidth() + dw));
            s.setHeight(Math.max(0.25F, s.getHeight() + dh));
            return true;
        }, done);
    }

    public void setScreenSize(UUID uuid, String periphId, float w, float h, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setWidth(Math.max(0.25F, w));
            s.setHeight(Math.max(0.25F, h));
            return true;
        }, done);
    }

    public void setScreenOffset(UUID uuid, String periphId, float x, float y, float z, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setOffsetX(x);
            s.setOffsetY(y);
            s.setOffsetZ(z);
            return true;
        }, done);
    }

    public void setScreenOffsetToPlayer(UUID uuid, String periphId, Player player, Consumer<Boolean> done) {
        if (player == null) {
            done.accept(Boolean.FALSE);
            return;
        }
        var target = player.getLocation();
        withDisplay(uuid, display -> {
            var displayLocation = display.getLocation();
            if (!sameWorld(displayLocation, target)) {
                return Boolean.FALSE;
            }
            var snapshot = readFromEntity(display);
            var screen = snapshot.findScreen(periphId);
            if (screen == null) {
                return Boolean.FALSE;
            }
            var offset = worldOffsetToLocal(displayLocation, target);
            screen.setOffsetX(offset.x());
            screen.setOffsetY(offset.y());
            screen.setOffsetZ(offset.z());
            applyEntityState(display, snapshot);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public void setScreenBrightness(UUID uuid, String periphId, int v, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setMinBrightness(Math.max(0, Math.min(15, v)));
            return true;
        }, done);
    }

    public void setScreenFillMode(UUID uuid, String periphId, String mode, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setFillMode(mode);
            return true;
        }, done);
    }

    public void setScreenDanmakuVisible(UUID uuid, String periphId, boolean visible, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setDanmakuVisible(visible);
            return true;
        }, done);
    }

    public void toggleScreenFill(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setFillMode("fill".equalsIgnoreCase(s.getFillMode()) ? "keep_aspect_cover" : "fill");
            return true;
        }, done);
    }

    public void setScreenRotation(UUID uuid, String periphId, float rx, float ry, float rz, float rw, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setOffsetRx(rx);
            s.setOffsetRy(ry);
            s.setOffsetRz(rz);
            s.setOffsetRw(rw);
            return true;
        }, done);
    }

    public void setSpeakerVolume(UUID uuid, String periphId, float v, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findSpeaker(periphId);
            if (s == null) return false;
            s.setVolume(Math.max(0.0F, Math.min(4.0F, v)));
            return true;
        }, done);
    }

    public void setMasterVolume(UUID uuid, float v, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            p.setMasterVolume(v);
            return true;
        }, done);
    }

    public void setPowered(UUID uuid, boolean powered, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            p.setPowered(powered);
            return true;
        }, done);
    }

    public void setMaxActiveRange(UUID uuid, float maxActiveRange, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            p.setMaxActiveRange(maxActiveRange);
            return true;
        }, done);
    }

    public void setPublicAsync(UUID uuid, boolean isPublic, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            p.setPublic(isPublic);
            return true;
        }, done);
    }

    public void setSpeakerRange(UUID uuid, String periphId, float v, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findSpeaker(periphId);
            if (s == null) return false;
            s.setMaxRange(Math.max(1.0F, v));
            return true;
        }, done);
    }

    public void setSpeakerChannelMode(UUID uuid, String periphId, String mode, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findSpeaker(periphId);
            if (s == null) return false;
            s.setChannelMode(mode);
            return true;
        }, done);
    }

    public void setSpeakerOffset(UUID uuid, String periphId, float x, float y, float z, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findSpeaker(periphId);
            if (s == null) {
                LOGGER.warn("setSpeakerOffset: speaker {} not found in entity {}", periphId, uuid);
                return false;
            }
            s.setOffsetX(x);
            s.setOffsetY(y);
            s.setOffsetZ(z);
            return true;
        }, done);
    }

    public void setSpeakerOffsetToPlayer(UUID uuid, String periphId, Player player, Consumer<Boolean> done) {
        if (player == null) {
            done.accept(Boolean.FALSE);
            return;
        }
        var target = player.getLocation();
        withDisplay(uuid, display -> {
            var displayLocation = display.getLocation();
            if (!sameWorld(displayLocation, target)) {
                return Boolean.FALSE;
            }
            var snapshot = readFromEntity(display);
            var speaker = snapshot.findSpeaker(periphId);
            if (speaker == null) {
                LOGGER.warn("setSpeakerOffsetToPlayer: speaker {} not found in entity {}", periphId, uuid);
                return Boolean.FALSE;
            }
            var offset = worldOffsetToLocal(displayLocation, target);
            speaker.setOffsetX(offset.x());
            speaker.setOffsetY(offset.y());
            speaker.setOffsetZ(offset.z());
            applyEntityState(display, snapshot);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public void resetScreenSize(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.resetSize();
            return true;
        }, done);
    }

    public void resetScreenBasic(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.resetBasic();
            return true;
        }, done);
    }

    public void resetScreenOffset(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.resetOffset();
            return true;
        }, done);
    }

    public void resetScreenRotation(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.resetRotation();
            return true;
        }, done);
    }

    public void resetSpeakerAudio(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findSpeaker(periphId);
            if (s == null) return false;
            s.resetAudio();
            return true;
        }, done);
    }

    public void resetSpeakerOffset(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findSpeaker(periphId);
            if (s == null) return false;
            s.resetOffset();
            return true;
        }, done);
    }

    public void removePeripheral(UUID uuid, String kind, String id, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            if ("screen".equals(kind)) {
                return p.getScreens().removeIf(s -> s.getId().equals(id));
            }
            if ("speaker".equals(kind)) {
                return p.getSpeakers().removeIf(s -> s.getId().equals(id));
            }
            return false;
        }, done);
    }

    public void addScreen(UUID uuid, Consumer<ScreenPeripheralConfigModel> done) {
        withDisplay(uuid, display -> {
            var player = readFromEntity(display);
            var screen = player.addScreen();
            applyEntityState(display, player);
            return screen;
        }, done);
    }

    public void addSpeaker(UUID uuid, Consumer<SpeakerPeripheralConfigModel> done) {
        withDisplay(uuid, display -> {
            var player = readFromEntity(display);
            var speaker = player.addSpeaker();
            applyEntityState(display, player);
            return speaker;
        }, done);
    }

    public void updateName(UUID uuid, String name, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            p.setName(name);
            return true;
        }, done);
    }

    public void snapScreenOffset(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findScreen(periphId);
            if (s == null) return false;
            s.setOffsetX(Math.round(s.getOffsetX()));
            s.setOffsetY(Math.round(s.getOffsetY()));
            s.setOffsetZ(Math.round(s.getOffsetZ()));
            return true;
        }, done);
    }

    public void snapSpeakerOffset(UUID uuid, String periphId, Consumer<Boolean> done) {
        mutate(uuid, p -> {
            var s = p.findSpeaker(periphId);
            if (s == null) return false;
            s.setOffsetX(Math.round(s.getOffsetX()));
            s.setOffsetY(Math.round(s.getOffsetY()));
            s.setOffsetZ(Math.round(s.getOffsetZ()));
            return true;
        }, done);
    }

    public void snapEntityPosition(UUID uuid, Consumer<Boolean> done) {
        withDisplay(uuid, display -> {
            var loc = display.getLocation();
            loc.setX(Math.round(loc.getX()));
            loc.setY(Math.round(loc.getY()));
            loc.setZ(Math.round(loc.getZ()));
            display.teleportAsync(loc);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public void teleportToPlayer(UUID uuid, Player player, Consumer<Boolean> done) {
        if (player == null) {
            done.accept(Boolean.FALSE);
            return;
        }
        player.getScheduler().run(plugin, task -> {
            var target = player.getLocation();
            withDisplay(uuid, display -> {
                display.teleportAsync(target);
                return Boolean.TRUE;
            }, result -> done.accept(Boolean.TRUE.equals(result)));
        }, () -> done.accept(Boolean.FALSE));
    }

    public void moveEntity(UUID uuid, double dx, double dy, double dz, Consumer<Boolean> done) {
        withDisplay(uuid, display -> {
            var loc = display.getLocation();
            loc.add(dx, dy, dz);
            display.teleportAsync(loc);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public void setEntityRotation(UUID uuid, float yaw, float pitch, Consumer<Boolean> done) {
        withDisplay(uuid, display -> {
            var loc = display.getLocation();
            loc.setYaw(yaw);
            loc.setPitch(pitch);
            display.teleportAsync(loc);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public boolean isManagedItemDisplay(Entity entity) {
        return entity instanceof ItemDisplay itemDisplay
                && itemDisplay.getScoreboardTags().contains(InteractionDataCommandBridge.PLAYER_TAG);
    }

    public void applyEntityState(ItemDisplay itemDisplay, ManagedMtvPlayer player) {
        itemDisplay.customName(Component.text("mtv:" + player.getName()));
        itemDisplay.setCustomNameVisible(false);
        player.captureLocation(itemDisplay.getLocation());
        InteractionDataCommandBridge.apply(itemDisplay, player);
    }

    public static boolean canEditPlayer(Player player, ManagedMtvPlayer snapshot) {
        if (snapshot == null) return false;
        if (snapshot.isPublic()) return true;
        if (snapshot.getOwner() == null) return true;
        if (player == null) return false;
        return player.getUniqueId().equals(snapshot.getOwner())
                || player.hasPermission("mtv.player.edit.others");
    }

    private ItemDisplay spawnItemDisplay(Location location) {
        ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class);
        itemDisplay.setRotation(location.getYaw(), location.getPitch());
        itemDisplay.setPersistent(true);
        return itemDisplay;
    }

    public void updateChannelBinding(UUID uuid, MtvChannelBinding binding, Consumer<Boolean> done) {
        withDisplay(uuid, display -> {
            var player = readFromEntity(display);
            player.setChannelBinding(binding);
            if (binding.isBroadcast() && channelService.getPublicChannel(binding.channelId()) == null) {
                return Boolean.FALSE;
            }
            applyEntityState(display, player);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public void withManagedPlayer(UUID uuid, Function<ManagedMtvPlayer, Boolean> action, Consumer<Boolean> done) {
        withDisplay(uuid, display -> action.apply(readFromEntity(display)), result -> done.accept(Boolean.TRUE.equals(result)));
    }

    private <T> void withDisplay(UUID uuid, Function<ItemDisplay, T> action, Consumer<T> done) {
        Entity entity = Bukkit.getEntity(uuid);
        if (!(entity instanceof ItemDisplay display)) {
            done.accept(null);
            return;
        }
        display.getScheduler().run(plugin, task -> done.accept(action.apply(display)), () -> done.accept(null));
    }

    private void runOnPlayer(Player player, Runnable action) {
        if (player == null || action == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> action.run(), null);
    }

    private static boolean sameWorld(Location left, Location right) {
        return left.getWorld() != null
                && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID());
    }

    private static Vector3f worldOffsetToLocal(Location origin, Location target) {
        var offset = new Vector3f(
                (float) (target.getX() - origin.getX()),
                (float) (target.getY() - origin.getY()),
                (float) (target.getZ() - origin.getZ())
        );
        new Quaternionf()
                .rotateX((float) Math.toRadians(origin.getPitch()))
                .rotateY((float) Math.toRadians(origin.getYaw()))
                .transform(offset);
        return offset;
    }

}
