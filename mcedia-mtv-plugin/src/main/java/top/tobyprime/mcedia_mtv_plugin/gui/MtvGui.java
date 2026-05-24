package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MtvGui {
    public enum GuiType {
        MAIN_MENU,
        PLAYER_MENU,
        PERIPHERAL_LIST,
        SCREEN_SETTINGS,
        SPEAKER_SETTINGS,
        ADD_PERIPHERAL,
        WORLD_TRANSFORM
    }

    public static class MtvHolder implements InventoryHolder {
        private final GuiType type;
        private final UUID entityUuid;
        private final String peripheralId;

        public MtvHolder(GuiType type, UUID entityUuid, String peripheralId) {
            this.type = type;
            this.entityUuid = entityUuid;
            this.peripheralId = peripheralId;
        }

        public GuiType getType() { return type; }
        public UUID getEntityUuid() { return entityUuid; }
        public String getPeripheralId() { return peripheralId; }
        @Override public Inventory getInventory() { throw new UnsupportedOperationException(); }
    }

    public static class GuiState {
        private final GuiType type;
        private final UUID entityUuid;
        private final Map<String, String> temp = new HashMap<>();

        public GuiState(GuiType type, UUID entityUuid) { this.type = type; this.entityUuid = entityUuid; }
        public GuiType getType() { return type; }
        public UUID getEntityUuid() { return entityUuid; }
        public Map<String, String> getTemp() { return temp; }
    }

    private final JavaPlugin plugin;
    private final MtvPlayerManager manager;
    private final MtvPeripheralController controller;
    private final MtvPlaybackController playbackController;
    private final Map<UUID, GuiState> playerStates = new ConcurrentHashMap<>();

    public MtvGui(JavaPlugin plugin, MtvPlayerManager manager, MtvPeripheralController controller, MtvPlaybackController playbackController) { this.plugin = plugin; this.manager = manager; this.controller = controller; this.playbackController = playbackController; }

    public void openMainMenu(Player player) {
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.MAIN_MENU, null, null), 27, Component.text("MTV"));
        inv.setItem(11, item(Material.ITEM_FRAME, "创建 MTV"));
        inv.setItem(15, item(Material.ENDER_PEARL, "编辑最近的 MTV", "对着实体潜行右键也可"));
        fillBorder27(inv);
        setState(player, GuiType.MAIN_MENU, null);
        player.openInventory(inv);
    }

    public void openPlayerMenu(Player player, ManagedMtvPlayer snapshot) {
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.PLAYER_MENU, snapshot.getUuid(), null), 54, Component.text("MTV: " + snapshot.getName()));
        inv.setItem(4, item(Material.NAME_TAG, snapshot.getName()));
        inv.setItem(11, item(Material.CLOCK, "速度: " + snapshot.getSpeed() + "x", "左键 -0.25 / 右键 +0.25"));
        inv.setItem(12, item(Material.ITEM_FRAME, "外设列表", "屏幕 / 扬声器"));
        inv.setItem(13, item(Material.COMPASS, "位置与朝向", "移动 / 旋转实体"));
        inv.setItem(15, item(Material.ENDER_PEARL, "传送到实体"));
        inv.setItem(40, item(Material.MUSIC_DISC_CAT, "设置播放链接", snapshot.getMediaUrl().isBlank() ? "未设置" : snapshot.getMediaUrl(), "输入 URL 或 BV 号"));
        inv.setItem(47, item(Material.STRUCTURE_VOID, "从头播放"));
        inv.setItem(48, item(Material.STONE_BUTTON, "后退 1 秒", "潜行点击后退 10 秒"));
        var pauseIcon = snapshot.isPaused() ? Material.YELLOW_WOOL : Material.RED_WOOL;
        var pauseName = snapshot.isPaused() ? "▶ 播放" : "⏸ 暂停";
        inv.setItem(49, item(pauseIcon, pauseName));
        inv.setItem(50, item(Material.STONE_BUTTON, "前进 1 秒", "潜行点击前进 10 秒"));
        inv.setItem(51, item(Material.COMPASS, "设置到位置: " + formatDurationUs(snapshot.getStartAt()), "点击输入微秒值"));
        inv.setItem(53, item(Material.TNT, "删除 MTV"));
        fillBorder54(inv);
        setState(player, GuiType.PLAYER_MENU, snapshot.getUuid());
        player.openInventory(inv);
    }

    private static String formatDurationUs(long us) {
        long totalSec = us / 1_000_000;
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        return String.format("%d:%02d", minutes, seconds);
    }

    public void openPeripheralList(Player player, ManagedMtvPlayer snapshot) {
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.PERIPHERAL_LIST, snapshot.getUuid(), null), 54, Component.text("外设 - " + snapshot.getName()));
        inv.setItem(4, item(Material.NAME_TAG, snapshot.getName()));

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int idx = 0;

        for (var s : snapshot.getScreens()) {
            if (idx >= slots.length) break;
            inv.setItem(slots[idx++], item(Material.MAP, "屏幕 [" + s.getId() + "]",
                    s.getWidth() + " x " + s.getHeight(), "亮度: " + s.getMinBrightness(), "点击编辑"));
        }
        for (var s : snapshot.getSpeakers()) {
            if (idx >= slots.length) break;
            inv.setItem(slots[idx++], item(Material.NOTE_BLOCK, "扬声器 [" + s.getId() + "]",
                    "音量: " + s.getVolume(), "范围: " + s.getMaxRange(), "点击编辑"));
        }

        inv.setItem(43, item(Material.GREEN_WOOL, "新增外设"));
        inv.setItem(49, item(Material.ARROW, "返回"));
        fillBorder54(inv);
        setState(player, GuiType.PERIPHERAL_LIST, snapshot.getUuid());
        player.openInventory(inv);
    }

    public void openScreenSettings(Player player, ManagedMtvPlayer snapshot, String periphId) {
        var sc = snapshot.findScreen(periphId);
        if (sc == null) { player.closeInventory(); return; }

        var inv = Bukkit.createInventory(new MtvHolder(GuiType.SCREEN_SETTINGS, snapshot.getUuid(), periphId), 54, Component.text("屏幕 " + periphId + " - " + snapshot.getName()));
        inv.setItem(4, item(Material.NAME_TAG, "屏幕 " + periphId, sc.getWidth() + " x " + sc.getHeight()));
        inv.setItem(10, item(Material.MAP, "宽: " + String.format("%.1f", sc.getWidth()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(11, item(Material.MAP, "高: " + String.format("%.1f", sc.getHeight()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(12, item(Material.STRUCTURE_VOID, "重置尺寸", "点击恢复默认"));
        inv.setItem(19, item(Material.SEA_LANTERN, "亮度: " + sc.getMinBrightness(), "左键 -1 / 右键 +1"));
        inv.setItem(20, item(Material.PAINTING, "填充: " + sc.getFillMode(), "点击切换"));
        inv.setItem(21, item(Material.STRUCTURE_VOID, "重置基础", "亮度/填充/纹理"));
        inv.setItem(22, item(sc.isDanmakuVisible() ? Material.LIME_DYE : Material.GRAY_DYE,
                "弹幕: " + (sc.isDanmakuVisible() ? "开" : "关"), "点击切换"));
        inv.setItem(28, item(Material.RED_WOOL, "X: " + String.format("%.1f", sc.getOffsetX()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(29, item(Material.GREEN_WOOL, "Y: " + String.format("%.1f", sc.getOffsetY()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(30, item(Material.BLUE_WOOL, "Z: " + String.format("%.1f", sc.getOffsetZ()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(31, item(Material.STRUCTURE_VOID, "重置偏移", "点击恢复默认"));
        inv.setItem(32, item(Material.LIGHTNING_ROD, "吸附偏移", "四舍五入到整数"));
        inv.setItem(33, item(Material.ENDER_EYE, "设到玩家位置", "偏移设为你脚下"));

        float[] euler = top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
        inv.setItem(37, item(Material.COMPASS, "Roll(Z): " + String.format("%.0f", euler[0]) + "°", "左键 -1 / 右键 +1"));
        inv.setItem(38, item(Material.COMPASS, "Pitch(Y): " + String.format("%.0f", euler[1]) + "°", "左键 -1 / 右键 +1"));
        inv.setItem(39, item(Material.COMPASS, "Yaw(X): " + String.format("%.0f", euler[2]) + "°", "左键 -1 / 右键 +1"));
        inv.setItem(40, item(Material.STRUCTURE_VOID, "重置旋转", "点击恢复默认"));
        inv.setItem(49, item(Material.ARROW, "返回外设列表"));
        inv.setItem(53, item(Material.TNT, "删除外设"));

        fillBorder54(inv);
        setState(player, GuiType.SCREEN_SETTINGS, snapshot.getUuid());
        player.openInventory(inv);
    }

    public void openSpeakerSettings(Player player, ManagedMtvPlayer snapshot, String periphId) {
        var sp = snapshot.findSpeaker(periphId);
        if (sp == null) { player.closeInventory(); return; }

        var inv = Bukkit.createInventory(new MtvHolder(GuiType.SPEAKER_SETTINGS, snapshot.getUuid(), periphId), 54, Component.text("扬声器 " + periphId + " - " + snapshot.getName()));
        inv.setItem(4, item(Material.NAME_TAG, "扬声器 " + periphId));
        inv.setItem(10, item(Material.NOTE_BLOCK, "音量: " + String.format("%.1f", sp.getVolume()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(11, item(Material.BELL, "范围: " + String.format("%.0f", sp.getMaxRange()), "左键 -1 / 右键 +1"));
        inv.setItem(12, item(Material.JUKEBOX, "声道: " + sp.getChannelMode(), "点击切换 mix/left/right"));
        inv.setItem(13, item(Material.STRUCTURE_VOID, "重置音域", "点击恢复默认"));
        inv.setItem(28, item(Material.RED_WOOL, "X: " + String.format("%.1f", sp.getOffsetX()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(29, item(Material.GREEN_WOOL, "Y: " + String.format("%.1f", sp.getOffsetY()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(30, item(Material.BLUE_WOOL, "Z: " + String.format("%.1f", sp.getOffsetZ()), "左键 -0.1 / 右键 +0.1"));
        inv.setItem(31, item(Material.STRUCTURE_VOID, "重置偏移", "点击恢复默认"));
        inv.setItem(32, item(Material.LIGHTNING_ROD, "吸附偏移", "四舍五入到整数"));
        inv.setItem(33, item(Material.ENDER_EYE, "设到玩家位置", "偏移设为你脚下"));
        inv.setItem(49, item(Material.ARROW, "返回外设列表"));
        inv.setItem(53, item(Material.TNT, "删除外设"));

        fillBorder54(inv);
        setState(player, GuiType.SPEAKER_SETTINGS, snapshot.getUuid());
        player.openInventory(inv);
    }

    public void openWorldTransform(Player player, ManagedMtvPlayer snapshot) {
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.WORLD_TRANSFORM, snapshot.getUuid(), null), 54, Component.text("位置与朝向 - " + snapshot.getName()));
        inv.setItem(4, item(Material.NAME_TAG, snapshot.getName()));
        inv.setItem(10, item(Material.RED_WOOL, "X: " + String.format("%.1f", snapshot.getX()), "左键 -0.5 / 右键 +0.5"));
        inv.setItem(11, item(Material.GREEN_WOOL, "Y: " + String.format("%.1f", snapshot.getY()), "左键 -0.5 / 右键 +0.5"));
        inv.setItem(12, item(Material.BLUE_WOOL, "Z: " + String.format("%.1f", snapshot.getZ()), "左键 -0.5 / 右键 +0.5"));
        inv.setItem(15, item(Material.LIGHTNING_ROD, "吸附到网格", "四舍五入到整数坐标"));
        inv.setItem(16, item(Material.ENDER_EYE, "传送到此处", "将实体传送到你脚下"));
        inv.setItem(19, item(Material.COMPASS, "朝向: " + String.format("%.0f", snapshot.getYaw()) + "°", "左键 -5 / 右键 +5"));
        inv.setItem(20, item(Material.COMPASS, "俯仰: " + String.format("%.0f", snapshot.getPitch()) + "°", "左键 -5 / 右键 +5"));
        inv.setItem(21, item(Material.STRUCTURE_VOID, "重置朝向", "恢复默认朝向"));
        inv.setItem(49, item(Material.ARROW, "返回"));
        fillBorder54(inv);
        setState(player, GuiType.WORLD_TRANSFORM, snapshot.getUuid());
        player.openInventory(inv);
    }

    public void openAddPeripheral(Player player, ManagedMtvPlayer snapshot) {
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.ADD_PERIPHERAL, snapshot.getUuid(), null), 27, Component.text("新增外设 - " + snapshot.getName()));
        inv.setItem(11, item(Material.MAP, "屏幕"));
        inv.setItem(15, item(Material.NOTE_BLOCK, "扬声器"));
        fillBorder27(inv);
        setState(player, GuiType.ADD_PERIPHERAL, snapshot.getUuid());
        player.openInventory(inv);
    }

    private void setState(Player player, GuiType type, UUID uuid) {
        playerStates.put(player.getUniqueId(), new GuiState(type, uuid));
    }

    public GuiState getState(Player player) { return playerStates.get(player.getUniqueId()); }
    public MtvPlayerManager getManager() { return manager; }
    public MtvPeripheralController getController() { return controller; }
    public JavaPlugin getPlugin() { return plugin; }

    public void setAwaitingInput(Player player, GuiType type, UUID entityUuid, String kind) {
        GuiState state = new GuiState(type, entityUuid);
        state.getTemp().put("awaiting", kind);
        playerStates.put(player.getUniqueId(), state);
    }

    public boolean handleChatInput(Player player, String message) {
        GuiState state = playerStates.remove(player.getUniqueId());
        if (state == null) return false;
        String awaiting = state.getTemp().get("awaiting");
        if (awaiting == null) return false;

        String input = message.trim();
        if (state.getEntityUuid() == null) {
            if (!"create_name".equals(awaiting)) return false;
            if (input.isBlank()) {
                runOnPlayer(player, () -> player.sendMessage("名称不能为空。"));
                return true;
            }
            player.performCommand("mtv create " + input);
            return true;
        }

        manager.readSnapshot(state.getEntityUuid(), snapshot -> {
            if (snapshot == null) {
                runOnPlayer(player, () -> player.sendMessage("该实体已不存在。"));
                return;
            }

            switch (awaiting) {
                case "media_url" -> playbackController.updateMediaUrl(state.getEntityUuid(), input,
                        success -> runOnPlayer(player, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                player.sendMessage("设置播放 URL 失败。");
                                return;
                            }
                            reopenPage(player, state.getType(), state.getEntityUuid());
                        }));
                case "start_at" -> {
                    long startAt;
                    try {
                        startAt = Long.parseLong(input);
                    } catch (NumberFormatException e) {
                        runOnPlayer(player, () -> player.sendMessage("请输入有效的微秒整数。"));
                        return;
                    }
                    playbackController.updateStartAt(state.getEntityUuid(), startAt,
                            success -> runOnPlayer(player, () -> {
                                if (!Boolean.TRUE.equals(success)) {
                                    player.sendMessage("设置播放位置失败。");
                                    return;
                                }
                                reopenPage(player, state.getType(), state.getEntityUuid());
                            }));
                }
                case "rename" -> manager.updateName(state.getEntityUuid(), input,
                        success -> runOnPlayer(player, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                player.sendMessage("重命名失败。");
                                return;
                            }
                            reopenPage(player, state.getType(), state.getEntityUuid());
                        }));
                default -> {
                }
            }
        });
        return true;
    }

    public void reopenPage(Player player, GuiType type, UUID uuid) {
        reopenPage(player, type, uuid, null);
    }

    public void reopenPage(Player player, GuiType type, UUID uuid, String periphId) {
        manager.readSnapshot(uuid, snapshot -> {
            if (snapshot == null) {
                return;
            }
            runOnPlayer(player, () -> {
                switch (type) {
                    case PERIPHERAL_LIST -> openPeripheralList(player, snapshot);
                    case WORLD_TRANSFORM -> openWorldTransform(player, snapshot);
                    case SCREEN_SETTINGS -> {
                        if (periphId != null) {
                            openScreenSettings(player, snapshot, periphId);
                        } else {
                            openPeripheralList(player, snapshot);
                        }
                    }
                    case SPEAKER_SETTINGS -> {
                        if (periphId != null) {
                            openSpeakerSettings(player, snapshot, periphId);
                        } else {
                            openPeripheralList(player, snapshot);
                        }
                    }
                    default -> openPlayerMenu(player, snapshot);
                }
            });
        });
    }

    static String extractEntityName(ItemDisplay display) {
        var customName = display.customName();
        if (customName != null) {
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName);
            if (text.startsWith("mtv:")) return text.substring(4);
            return text;
        }
        return "mtv-" + display.getEntityId();
    }

    private void runOnPlayer(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(Component::text).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static void fillBorder27(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if ((row == 0 || row == 2 || col == 0 || col == 8) && inv.getItem(slot) == null) inv.setItem(slot, filler);
        }
    }

    private static void fillBorder54(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if ((row == 0 || row == 5 || col == 0 || col == 8) && inv.getItem(slot) == null) inv.setItem(slot, filler);
        }
    }
}
