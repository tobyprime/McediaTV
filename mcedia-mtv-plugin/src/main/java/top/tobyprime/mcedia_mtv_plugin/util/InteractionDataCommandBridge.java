package top.tobyprime.mcedia_mtv_plugin.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;
import top.tobyprime.mcedia_mtv_plugin.model.ControlAccess;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.model.ScreenPeripheralConfigModel;
import top.tobyprime.mcedia_mtv_plugin.model.SpeakerPeripheralConfigModel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class InteractionDataCommandBridge {
    public static final String PLAYER_TAG = "mcedia_mtv";
    public static final String ENTITY_CONFIG_KEY = "entity_config";
    public static final String CHANNEL_BINDING_KEY = "channel_binding";
    public static final String SCHEMA_VERSION_KEY = "schema_version";
    public static final int SCHEMA_VERSION = 6;

    private InteractionDataCommandBridge() {
    }

    public static void apply(ItemDisplay itemDisplay, ManagedMtvPlayer player) {
        itemDisplay.addScoreboardTag(PLAYER_TAG);
        itemDisplay.addScoreboardTag(SCHEMA_VERSION_KEY + ":" + SCHEMA_VERSION);

        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA_VERSION_KEY, SCHEMA_VERSION);

        CompoundTag entityConfig = new CompoundTag();
        entityConfig.putString("name", player.getName());
        entityConfig.putString("world", player.getWorld());
        entityConfig.putDouble("x", player.getX());
        entityConfig.putDouble("y", player.getY());
        entityConfig.putDouble("z", player.getZ());
        entityConfig.putFloat("yaw", player.getYaw());
        entityConfig.putFloat("pitch", player.getPitch());
        entityConfig.putFloat("master_volume", player.getMasterVolume());
        entityConfig.putFloat("max_active_range", player.getMaxActiveRange());
        entityConfig.putBoolean("powered", player.isPowered());
        if (player.getOwner() != null) {
            entityConfig.putString("owner", player.getOwner().toString());
        }
        entityConfig.putBoolean("is_public", player.getControlAccess() == ControlAccess.PUBLIC);
        entityConfig.putString("control_access", player.getControlAccess().name());

        ListTag peripherals = new ListTag();
        for (var s : player.getScreens()) {
            peripherals.add(buildScreenTag(s));
        }
        for (var s : player.getSpeakers()) {
            peripherals.add(buildSpeakerTag(s));
        }
        entityConfig.put("peripherals", peripherals);
        root.put(ENTITY_CONFIG_KEY, entityConfig);

        var binding = player.getChannelBinding();
        if (binding == null) {
            binding = MtvChannelBinding.self();
        }
        CompoundTag channelBinding = new CompoundTag();
        channelBinding.putString("type", binding.type().name().toLowerCase());
        if (binding.isBroadcast() && binding.channelId() != null && !binding.channelId().isBlank()) {
            channelBinding.putString("channel_id", binding.channelId());
        }
        if (binding.regionKey() != null) {
            channelBinding.putString("region_key", binding.regionKey());
        }
        root.put(CHANNEL_BINDING_KEY, channelBinding);

        var stack = new ItemStack(Material.PAINTING);
        var nmsStack = CraftItemStack.asNMSCopy(stack);
        nmsStack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        itemDisplay.setItemStack(toBukkitCopy(nmsStack));
    }

    /**
     * Paper 26.3 把 {@code CraftItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack)} 的参数
     * 收窄为新的 {@code ItemInstance} 接口并移除了 ItemStack 重载（26.2 及更早版本则只有 ItemStack 重载）。
     * 单一静态签名无法同时链接 1.21.11 / 26.2 / 26.3，因此按运行期参数类型解析后反射调用；
     * Paper 的插件重映射会把 NMS 类字面量与运行期映射保持一致，跨映射命名空间仍然成立。
     */
    private static final Method AS_BUKKIT_COPY = resolveAsBukkitCopy();

    private static Method resolveAsBukkitCopy() {
        Method compatible = null;
        for (Method method : CraftItemStack.class.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !method.getName().equals("asBukkitCopy")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || method.getReturnType() != ItemStack.class) {
                continue;
            }
            if (params[0] == net.minecraft.world.item.ItemStack.class) {
                return method;
            }
            if (params[0].isAssignableFrom(net.minecraft.world.item.ItemStack.class)) {
                compatible = method;
            }
        }
        return compatible;
    }

    private static ItemStack toBukkitCopy(net.minecraft.world.item.ItemStack nmsStack) {
        if (AS_BUKKIT_COPY == null) {
            throw new IllegalStateException("CraftItemStack.asBukkitCopy(ItemStack-compatible) not found on this server");
        }
        try {
            return (ItemStack) AS_BUKKIT_COPY.invoke(null, nmsStack);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke CraftItemStack.asBukkitCopy", e);
        }
    }

    private static CompoundTag buildScreenTag(ScreenPeripheralConfigModel screen) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "screen");
        tag.putString("id", screen.getId());
        tag.putFloat("offset_x", screen.getOffsetX());
        tag.putFloat("offset_y", screen.getOffsetY());
        tag.putFloat("offset_z", screen.getOffsetZ());
        tag.putFloat("offset_rx", screen.getOffsetRx());
        tag.putFloat("offset_ry", screen.getOffsetRy());
        tag.putFloat("offset_rz", screen.getOffsetRz());
        tag.putFloat("offset_rw", screen.getOffsetRw());
        tag.putInt("min_brightness", screen.getMinBrightness());
        tag.putFloat("width", screen.getWidth());
        tag.putFloat("height", screen.getHeight());
        tag.putString("fill_mode", screen.getFillMode());
        tag.putString("background_texture", screen.getBackgroundTexture());
        tag.putBoolean("danmaku_visible", screen.isDanmakuVisible());
        // MTV world UI owns the in-screen progress bar; keep Core's passive bar hidden.
        tag.putBoolean("progress_bar_visible", false);
        return tag;
    }

    private static CompoundTag buildSpeakerTag(SpeakerPeripheralConfigModel speaker) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "speaker");
        tag.putString("id", speaker.getId());
        tag.putFloat("offset_x", speaker.getOffsetX());
        tag.putFloat("offset_y", speaker.getOffsetY());
        tag.putFloat("offset_z", speaker.getOffsetZ());
        tag.putFloat("offset_rx", speaker.getOffsetRx());
        tag.putFloat("offset_ry", speaker.getOffsetRy());
        tag.putFloat("offset_rz", speaker.getOffsetRz());
        tag.putFloat("offset_rw", speaker.getOffsetRw());
        tag.putFloat("max_range", speaker.getMaxRange());
        tag.putFloat("volume", speaker.getVolume());
        tag.putString("channel_mode", speaker.getChannelMode());
        return tag;
    }
}
