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
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelPlaybackStatus;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
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
        NEARBY_PLAYER_LIST,
        PLAYER_MENU,
        PERIPHERAL_LIST,
        SCREEN_SETTINGS,
        SPEAKER_SETTINGS,
        ADD_PERIPHERAL,
        WORLD_TRANSFORM,
        CHANNEL_MENU,
        REMOTE_MENU,
        PUBLIC_CHANNEL_LIST,
        PUBLIC_CHANNEL_CREATE,
        PUBLIC_CHANNEL_MANAGE
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

    public static final double NEARBY_RANGE = 50.0;
    public static final String MEDIA_INPUT_HINT = "请输入支持的播放链接";
    public static final String MEDIA_INPUT_MESSAGE = MEDIA_INPUT_HINT + "。";
    private static final String NEARBY_PAGE_KEY = "nearby_page";
    private static final String NEARBY_SLOT_UUID_PREFIX = "nearby_slot_";

    public static final int[] CHANNEL_PLAYLIST_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    public static final int[] PUBLIC_CHANNEL_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final JavaPlugin plugin;
    private final MtvPlayerManager manager;
    private final MtvPeripheralController controller;
    private final MtvPlaybackController playbackController;
    private final Map<UUID, GuiState> playerStates = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public MtvGui(JavaPlugin plugin, MtvPlayerManager manager, MtvPeripheralController controller, MtvPlaybackController playbackController) {
        this.plugin = plugin;
        this.manager = manager;
        this.controller = controller;
        this.playbackController = playbackController;
    }

    public void openMainMenu(Player player) {
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.MAIN_MENU, null, null), 27, Component.text("MTV"));
        inv.setItem(10, item(Material.ENDER_PEARL, "控制最近的播放器", "按当前距离最近的 MTV 打开控制页"));
        inv.setItem(11, item(Material.COMPASS, "附近的播放器", "分页查看附近 MTV 并选择控制"));
        inv.setItem(12, item(Material.BOOK, "频道列表", "浏览全部公共频道"));
        inv.setItem(14, item(Material.PLAYER_HEAD, "我的频道", "只查看我创建的公共频道"));
        inv.setItem(15, item(Material.JUKEBOX, "控制最近播放器的频道", "按当前距离最近的 MTV 打开频道控制页"));
        fillBorder27(inv);
        setState(player, GuiType.MAIN_MENU, null);
        player.openInventory(inv);
    }

    public void openRemoteMenu(Player player) {
        manager.findNearbyAsync(player, NEARBY_RANGE, candidates -> runOnPlayer(player, () -> {
            if (candidates.isEmpty()) {
                player.sendMessage("附近 " + (int) NEARBY_RANGE + " 米内没有 MTV 播放器。");
                return;
            }
            openRemoteMenu(player, candidates.get(0));
        }));
    }

    public void openRemoteMenu(Player player, ManagedMtvPlayer snapshot) {
        var binding = manager.getChannelService().resolveBinding(snapshot);
        var publicChannel = binding.isBroadcast() ? manager.getChannelService().getPublicChannel(binding.channelId()) : null;
        var channelState = manager.getChannelService().ensureChannelState(binding.channelId());
        var playState = channelState != null ? channelState.getPlayState() : null;
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.REMOTE_MENU, snapshot.getUuid(), null), 27, Component.text("遥控器 - " + snapshot.getName()));
        inv.setItem(4, item(Material.IRON_DOOR,
                snapshot.getName(),
                "频道: " + fallback(publicChannel != null ? publicChannel.getChannelName() : null, binding.channelId()),
                "当前: " + summarize(playState != null ? playState.getMediaUrl() : ""),
                "模式: " + formatPlayOrderMode(channelState)));
        var powerIcon = snapshot.isPowered() ? Material.LIME_DYE : Material.GRAY_DYE;
        inv.setItem(10, item(powerIcon, "电源: " + (snapshot.isPowered() ? "开" : "关"), "点击切换待机状态"));
        inv.setItem(11, item(Material.NOTE_BLOCK, "总音量: " + String.format("%.2f", snapshot.getMasterVolume()), "左键 -0.1 / 右键 +0.1", "潜行改动 0.25"));
        inv.setItem(12, item(Material.STONE_BUTTON, "后退 1 秒", "潜行点击后退 10 秒"));
        inv.setItem(13, item(Material.MUSIC_DISC_CAT, "设置当前媒体", "切到该视频，并改为播完当前停止", binding.isBroadcast() ? "公共频道需要创建者或 OP" : "会覆盖当前列表"));
        inv.setItem(14, item(Material.STONE_BUTTON, "前进 1 秒", "潜行点击前进 10 秒"));
        inv.setItem(20, item(Material.ITEM_FRAME, "播放器入口", "打开当前最近播放器页"));
        inv.setItem(22, item(Material.SPYGLASS, "刷新目标", "重新选择最近的 MTV"));
        inv.setItem(24, item(Material.JUKEBOX, "频道入口", "打开当前最近播放器的频道页"));
        fillBorder27(inv);
        setState(player, GuiType.REMOTE_MENU, snapshot.getUuid());
        player.openInventory(inv);
    }

    public void openPlayerMenu(Player player, ManagedMtvPlayer snapshot) {
        var binding = manager.getChannelService().resolveBinding(snapshot);
        var publicChannel = binding.isBroadcast() ? manager.getChannelService().getPublicChannel(binding.channelId()) : null;
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.PLAYER_MENU, snapshot.getUuid(), null), 54, Component.text("MTV: " + snapshot.getName()));
        inv.setItem(4, item(Material.NAME_TAG, snapshot.getName()));
        inv.setItem(12, item(Material.ITEM_FRAME, "外设列表", "屏幕 / 扬声器"));
        inv.setItem(13, item(Material.COMPASS, "位置与朝向", "移动 / 旋转实体"));
        inv.setItem(15, item(Material.ENDER_PEARL, "传送到实体"));
        var powerIcon = snapshot.isPowered() ? Material.LIME_DYE : Material.GRAY_DYE;
        inv.setItem(10, item(powerIcon, "电源: " + (snapshot.isPowered() ? "开" : "关"), "点击切换待机状态"));
        inv.setItem(14, item(Material.NOTE_BLOCK, "总音量: " + String.format("%.2f", snapshot.getMasterVolume()), "左键 -0.1 / 右键 +0.1", "潜行改动 0.25"));
        if (binding.isBroadcast()) {
            inv.setItem(11, item(Material.BOOK,
                    "公共频道信息",
                    "频道: " + fallback(publicChannel != null ? publicChannel.getChannelName() : null, binding.channelId()),
                    "ID: " + binding.channelId(),
                    "点击查看频道信息"));
            inv.setItem(42, item(Material.BOOK, "切换绑定", "重新选择要绑定的公共频道"));
            inv.setItem(43, item(Material.STRUCTURE_VOID, "取消绑定", "切回私有 self 频道"));
        } else {
            var channelState = manager.getChannelService().ensureChannelState(binding.channelId());
            var playState = channelState != null ? channelState.getPlayState() : null;
            float speed = playState != null ? (float) playState.getSpeed() : 1.0F;
            String mediaUrl = playState != null ? playState.getMediaUrl() : "";
            boolean paused = playState != null ? playState.getState() != ChannelPlaybackStatus.PLAYING : false;
            long startAt = playState != null ? Math.max(0L, playState.getMediaTimeMs() * 1000L) : 0L;
            inv.setItem(11, item(Material.CLOCK, "速度: " + speed + "x", "左键 -0.25 / 右键 +0.25"));
            inv.setItem(41, item(Material.JUKEBOX, "频道编辑", "打开 channel 播放与列表页"));
            inv.setItem(42, item(Material.BOOK, "公共频道", "搜索并绑定公共频道"));
            inv.setItem(43, item(Material.STRUCTURE_VOID, "切回私有频道", "恢复 self 频道绑定"));
            inv.setItem(40, item(Material.MUSIC_DISC_CAT, "设置播放链接", mediaUrl.isBlank() ? "未设置" : mediaUrl, MEDIA_INPUT_HINT));
            inv.setItem(47, item(Material.STRUCTURE_VOID, "从头播放"));
            inv.setItem(48, item(Material.STONE_BUTTON, "后退 1 秒", "潜行点击后退 10 秒"));
            var pauseIcon = paused ? Material.YELLOW_WOOL : Material.RED_WOOL;
            var pauseName = paused ? "▶ 播放" : "⏸ 暂停";
            inv.setItem(49, item(pauseIcon, pauseName));
            inv.setItem(50, item(Material.STONE_BUTTON, "前进 1 秒", "潜行点击前进 10 秒"));
            inv.setItem(51, item(Material.COMPASS, "设置到位置: " + formatDurationUs(startAt), "点击输入微秒值"));
        }
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

    public void openChannelMenu(Player player, ManagedMtvPlayer snapshot) {
        var binding = manager.getChannelService().resolveBinding(snapshot);
        openChannelMenu(player, binding.channelId(), snapshot.getUuid());
    }

    public void openChannelMenu(Player player, String channelId, UUID entityUuid) {
        ChannelRuntimeState state = manager.getChannelService().ensureChannelState(channelId);
        if (state == null) {
            player.closeInventory();
            player.sendMessage("该频道不存在或无法加载。");
            return;
        }
        var playState = state.getPlayState();
        boolean paused = playState.getState() != ChannelPlaybackStatus.PLAYING;
        long startAtUs = Math.max(0L, playState.getMediaTimeMs() * 1000L);
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.CHANNEL_MENU, entityUuid, null), 54, Component.text("频道 - " + channelId));
        inv.setItem(4, item(Material.NAME_TAG, "频道: " + channelId, "模式: " + formatPlayOrderMode(state), "当前: " + currentPlaylistLabel(state)));
        inv.setItem(10, item(Material.CLOCK, "速度: " + playState.getSpeed() + "x", "左键 -0.25 / 右键 +0.25"));
        inv.setItem(11, item(Material.MUSIC_DISC_CAT, "设置当前媒体", playState.getMediaUrl().isBlank() ? "未设置" : summarize(playState.getMediaUrl()), "会替换整个播放列表"));
        inv.setItem(12, item(Material.COMPARATOR, "播放顺序", formatPlayOrderMode(state), "点击切换"));
        inv.setItem(13, item(Material.STONE_BUTTON, "上一首", "始终按列表顺序"));
        var pauseIcon = paused ? Material.YELLOW_WOOL : Material.RED_WOOL;
        var pauseName = paused ? "▶ 播放" : "⏸ 暂停";
        inv.setItem(14, item(pauseIcon, pauseName));
        inv.setItem(15, item(Material.STONE_BUTTON, "下一首", "始终按列表顺序"));
        inv.setItem(16, item(Material.COMPASS, "设置到位置: " + formatDurationUs(startAtUs), "点击输入微秒值"));
        inv.setItem(19, item(Material.HOPPER, "首加播放项", MEDIA_INPUT_HINT));
        inv.setItem(20, item(Material.CHEST, "尾加播放项", MEDIA_INPUT_HINT));
        inv.setItem(21, item(Material.STRUCTURE_VOID, "从头播放当前项"));
        inv.setItem(22, item(Material.STONE_BUTTON, "后退 1 秒", "潜行点击后退 10 秒"));
        inv.setItem(23, item(Material.STONE_BUTTON, "前进 1 秒", "潜行点击前进 10 秒"));
        inv.setItem(24, item(Material.BOOK, "列表信息", "项目数: " + state.getPlaylist().size(), "当前位置: " + (state.getPlaylist().isEmpty() ? "无" : (state.getPlaylistCursor() + 1))));
        inv.setItem(25, item(Material.LAVA_BUCKET, "清空播放列表", "清除所有播放项并停止播放"));
        if (entityUuid != null) {
            inv.setItem(47, item(Material.ARROW, "返回实体页"));
        } else {
            inv.setItem(47, item(Material.ARROW, "返回频道管理"));
        }
        inv.setItem(49, item(Material.SPYGLASS, "刷新"));

        for (int i = 0; i < CHANNEL_PLAYLIST_SLOTS.length && i < state.getPlaylist().size(); i++) {
            var entry = state.getPlaylist().get(i);
            boolean current = i == state.getPlaylistCursor();
            inv.setItem(CHANNEL_PLAYLIST_SLOTS[i], item(current ? Material.MUSIC_DISC_11 : Material.PAPER,
                    (current ? "▶ " : "") + "#" + (i + 1) + " " + summarize(entry.mediaUrl()),
                    "左键播放 / 右键删除",
                    "Shift左键移到最前 / Shift右键移到最后"));
        }

        fillBorder54(inv);
        if (entityUuid != null) {
            setState(player, GuiType.CHANNEL_MENU, entityUuid);
        } else {
            var temp = new HashMap<String, String>();
            temp.put("channel_id", channelId);
            setState(player, GuiType.CHANNEL_MENU, null, temp);
        }
        player.openInventory(inv);
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

    public void openNearbyPlayerList(Player player, int page) {
        manager.findNearbyAsync(player, NEARBY_RANGE, results -> runOnPlayer(player, () -> {
            int totalPages = Math.max(1, (results.size() + PUBLIC_CHANNEL_SLOTS.length - 1) / PUBLIC_CHANNEL_SLOTS.length);
            int normalizedPage = Math.max(0, Math.min(page, totalPages - 1));
            var inv = Bukkit.createInventory(new MtvHolder(GuiType.NEARBY_PLAYER_LIST, null, null), 54, Component.text("附近的播放器"));
            inv.setItem(4, item(Material.COMPASS, "附近的 MTV", "页码: " + (normalizedPage + 1) + "/" + totalPages, "结果数: " + results.size()));
            inv.setItem(45, item(Material.ARROW, "上一页"));
            inv.setItem(49, item(Material.SPYGLASS, "刷新"));
            inv.setItem(50, item(Material.ARROW, "返回主菜单"));
            inv.setItem(53, item(Material.ARROW, "下一页"));

            var origin = player.getLocation();
            int start = normalizedPage * PUBLIC_CHANNEL_SLOTS.length;
            var temp = new HashMap<String, String>();
            temp.put(NEARBY_PAGE_KEY, Integer.toString(normalizedPage));
            for (int i = 0; i < PUBLIC_CHANNEL_SLOTS.length && start + i < results.size(); i++) {
                int slot = PUBLIC_CHANNEL_SLOTS[i];
                var snapshot = results.get(start + i);
                double dist = Math.sqrt(origin.distanceSquared(snapshot.toLocation()));
                inv.setItem(slot, item(Material.ITEM_FRAME,
                        snapshot.getName(),
                        "距离: " + (int) dist + "m",
                        "频道: " + manager.getChannelService().resolveBinding(snapshot).channelId(),
                        "点击打开控制页"));
                temp.put(NEARBY_SLOT_UUID_PREFIX + slot, snapshot.getUuid().toString());
            }

            fillBorder54(inv);
            setState(player, GuiType.NEARBY_PLAYER_LIST, null, temp);
            player.openInventory(inv);
        }));
    }

    public void openNearestPlayerMenu(Player player) {
        openNearestPlayer(player, snapshot -> openPlayerMenu(player, snapshot), "附近 " + (int) NEARBY_RANGE + " 米内没有 MTV 播放器。");
    }

    public void openNearestPlayerChannelMenu(Player player) {
        openNearestPlayer(player, snapshot -> openChannelMenu(player, snapshot), "附近 " + (int) NEARBY_RANGE + " 米内没有 MTV 播放器。");
    }

    private void openNearestPlayer(Player player, java.util.function.Consumer<ManagedMtvPlayer> opener, String emptyMessage) {
        manager.findNearbyAsync(player, NEARBY_RANGE, candidates -> runOnPlayer(player, () -> {
            if (candidates.isEmpty()) {
                player.sendMessage(emptyMessage);
                openMainMenu(player);
                return;
            }
            opener.accept(candidates.get(0));
        }));
    }

    public void openPublicChannelList(Player player, UUID entityUuid, String query, int page, boolean ownOnly) {
        var results = manager.getChannelService().searchPublicChannels(query, player.getUniqueId(), ownOnly);
        int totalPages = Math.max(1, (results.size() + PUBLIC_CHANNEL_SLOTS.length - 1) / PUBLIC_CHANNEL_SLOTS.length);
        int normalizedPage = Math.max(0, Math.min(page, totalPages - 1));
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.PUBLIC_CHANNEL_LIST, entityUuid, null), 54, Component.text("公共频道"));
        inv.setItem(4, item(Material.BOOK, "公共频道目录", "搜索: " + (query == null || query.isBlank() ? "全部" : query), "页码: " + (normalizedPage + 1) + "/" + totalPages, "结果数: " + results.size()));
        inv.setItem(45, item(Material.ARROW, "上一页"));
        inv.setItem(46, item(ownOnly ? Material.LIME_DYE : Material.GRAY_DYE, ownOnly ? "只看我的频道" : "查看全部频道"));
        inv.setItem(47, item(Material.OAK_SIGN, "输入搜索词"));
        inv.setItem(48, item(Material.BARRIER, "清空搜索"));
        inv.setItem(49, item(Material.ANVIL, "创建公共频道"));
        inv.setItem(50, item(Material.ARROW, entityUuid != null ? "返回播放器页" : "返回主菜单"));
        inv.setItem(53, item(Material.ARROW, "下一页"));

        int start = normalizedPage * PUBLIC_CHANNEL_SLOTS.length;
        for (int i = 0; i < PUBLIC_CHANNEL_SLOTS.length && start + i < results.size(); i++) {
            var state = results.get(start + i);
            inv.setItem(PUBLIC_CHANNEL_SLOTS[i], item(Material.PAPER,
                    summarizePublicChannelName(state),
                    "创建者: " + fallback(state.getCreatorName(), "未知"),
                    "简介: " + fallback(summarize(state.getDescription()), "无"),
                    "观看中: " + manager.getChannelService().getAudienceCount(state.getChannelId()),
                    entityUuid != null ? "左键绑定当前播放器 / 右键管理" : "点击管理"));
        }

        fillBorder54(inv);
        var temp = new HashMap<String, String>();
        temp.put("public_query", query == null ? "" : query);
        temp.put("public_page", Integer.toString(normalizedPage));
        temp.put("public_own_only", Boolean.toString(ownOnly));
        setState(player, GuiType.PUBLIC_CHANNEL_LIST, entityUuid, temp);
        player.openInventory(inv);
    }

    public void openPublicChannelCreate(Player player, UUID entityUuid, String channelName, String description, String query, int page, boolean ownOnly) {
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.PUBLIC_CHANNEL_CREATE, entityUuid, null), 27, Component.text("创建公共频道"));
        inv.setItem(4, item(Material.BOOK, "创建公共频道", "名称: " + fallback(channelName, "未设置"), "介绍: " + fallback(description, "未设置")));
        inv.setItem(11, item(Material.NAME_TAG, "设置频道名称"));
        inv.setItem(13, item(Material.WRITABLE_BOOK, "设置频道介绍"));
        inv.setItem(15, item(Material.EMERALD_BLOCK, "确认创建"));
        inv.setItem(22, item(Material.ARROW, "返回列表"));
        fillBorder27(inv);
        var temp = new HashMap<String, String>();
        temp.put("public_channel_name", channelName == null ? "" : channelName);
        temp.put("public_channel_description", description == null ? "" : description);
        temp.put("public_query", query == null ? "" : query);
        temp.put("public_page", Integer.toString(page));
        temp.put("public_own_only", Boolean.toString(ownOnly));
        setState(player, GuiType.PUBLIC_CHANNEL_CREATE, entityUuid, temp);
        player.openInventory(inv);
    }

    public void openPublicChannelManage(Player player, UUID entityUuid, String channelId, String query, int page, boolean ownOnly) {
        var state = manager.getChannelService().getPublicChannel(channelId);
        if (state == null) {
            player.closeInventory();
            player.sendMessage("该公共频道不存在。");
            return;
        }
        boolean canManage = manager.getChannelService().canManagePublicChannel(player, state);
        var inv = Bukkit.createInventory(new MtvHolder(GuiType.PUBLIC_CHANNEL_MANAGE, entityUuid, channelId), 54, Component.text("公共频道管理"));
        inv.setItem(4, item(Material.BOOK, summarizePublicChannelName(state), "创建者: " + fallback(state.getCreatorName(), "未知"), "简介: " + fallback(state.getDescription(), "无")));
        inv.setItem(10, item(Material.PLAYER_HEAD, "创建者", fallback(state.getCreatorName(), "未知")));
        inv.setItem(11, item(Material.WRITABLE_BOOK, "频道介绍", fallback(state.getDescription(), "无")));
        inv.setItem(12, item(Material.ENDER_EYE, "当前观看人数", Integer.toString(manager.getChannelService().getAudienceCount(channelId))));
        inv.setItem(13, item(Material.NAME_TAG, "频道 ID", channelId));
        inv.setItem(14, item(Material.JUKEBOX,
                "频道控制",
                entityUuid != null ? "打开当前播放器的频道控制页" : "打开附近绑定此频道的播放器控制页",
                canManage ? "你可以编辑此频道" : "你当前只能只读查看"));
        if (canManage) {
            inv.setItem(15, item(Material.NAME_TAG, "编辑频道名称"));
            inv.setItem(16, item(Material.WRITABLE_BOOK, "编辑频道介绍"));
            inv.setItem(17, item(state.isPublicControl() ? Material.LIME_DYE : Material.GRAY_DYE,
                    "播放权限: " + (state.isPublicControl() ? "公开" : "私有"),
                    "公开: 所有人可控制播放",
                    "私有: 仅创建者和 OP 可控制播放"));
            inv.setItem(24, item(Material.TNT, "删除公共频道"));
        } else {
            inv.setItem(15, item(Material.BARRIER, "只读", "只有创建者或 OP 可以管理该频道"));
        }
        inv.setItem(49, item(Material.ARROW, "返回列表"));
        fillBorder54(inv);
        var temp = new HashMap<String, String>();
        temp.put("channel_id", channelId);
        temp.put("public_query", query == null ? "" : query);
        temp.put("public_page", Integer.toString(page));
        temp.put("public_own_only", Boolean.toString(ownOnly));
        setState(player, GuiType.PUBLIC_CHANNEL_MANAGE, entityUuid, temp);
        player.openInventory(inv);
    }

    private void setState(Player player, GuiType type, UUID uuid) {
        playerStates.put(player.getUniqueId(), new GuiState(type, uuid));
    }

    private void setState(Player player, GuiType type, UUID uuid, Map<String, String> temp) {
        GuiState state = new GuiState(type, uuid);
        state.getTemp().putAll(temp);
        playerStates.put(player.getUniqueId(), state);
    }

    public GuiState getState(Player player) { return playerStates.get(player.getUniqueId()); }
    public MtvPlayerManager getManager() { return manager; }
    public MtvPeripheralController getController() { return controller; }
    public MtvPlaybackController getPlaybackController() { return playbackController; }
    public JavaPlugin getPlugin() { return plugin; }

    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        playerStates.clear();
        for (var player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                var holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof MtvHolder) {
                    player.closeInventory();
                }
            }, null);
        }
    }

    public void setAwaitingInput(Player player, GuiType type, UUID entityUuid, String kind) {
        GuiState state = new GuiState(type, entityUuid);
        state.getTemp().put("awaiting", kind);
        playerStates.put(player.getUniqueId(), state);
    }

    public void setAwaitingInput(Player player, GuiState baseState, String kind) {
        GuiState state = new GuiState(baseState.getType(), baseState.getEntityUuid());
        state.getTemp().putAll(baseState.getTemp());
        state.getTemp().put("awaiting", kind);
        playerStates.put(player.getUniqueId(), state);
    }

    public boolean handleChatInput(Player player, String message) {
        if (closed) {
            return false;
        }
        GuiState state = playerStates.remove(player.getUniqueId());
        if (state == null) return false;
        String awaiting = state.getTemp().get("awaiting");
        if (awaiting == null) return false;

        String input = message.trim();
        switch (awaiting) {
            case "create_name" -> {
                if (input.isBlank()) {
                    runOnPlayer(player, () -> player.sendMessage("名称不能为空。"));
                    return true;
                }
                player.performCommand("mtv create " + input);
                return true;
            }
            case "public_channel_search" -> {
                runOnPlayer(player, () -> openPublicChannelList(player, state.getEntityUuid(), input, 0, isPublicOwnOnly(state)));
                return true;
            }
            case "public_channel_name" -> {
                runOnPlayer(player, () -> openPublicChannelCreate(player, state.getEntityUuid(), input, state.getTemp().getOrDefault("public_channel_description", ""), state.getTemp().getOrDefault("public_query", ""), parsePage(state), isPublicOwnOnly(state)));
                return true;
            }
            case "public_channel_description" -> {
                runOnPlayer(player, () -> openPublicChannelCreate(player, state.getEntityUuid(), state.getTemp().getOrDefault("public_channel_name", ""), input, state.getTemp().getOrDefault("public_query", ""), parsePage(state), isPublicOwnOnly(state)));
                return true;
            }
            case "public_channel_edit_name" -> {
                boolean success = manager.getChannelService().updatePublicChannelName(player, state.getTemp().get("channel_id"), input);
                runOnPlayer(player, () -> {
                    if (!success) {
                        player.sendMessage("更新频道名称失败。");
                        return;
                    }
                    openPublicChannelManage(player, state.getEntityUuid(), state.getTemp().get("channel_id"), state.getTemp().getOrDefault("public_query", ""), parsePage(state), isPublicOwnOnly(state));
                });
                return true;
            }
            case "public_channel_edit_description" -> {
                boolean success = manager.getChannelService().updatePublicChannelDescription(player, state.getTemp().get("channel_id"), input);
                runOnPlayer(player, () -> {
                    if (!success) {
                        player.sendMessage("更新频道介绍失败。");
                        return;
                    }
                    openPublicChannelManage(player, state.getEntityUuid(), state.getTemp().get("channel_id"), state.getTemp().getOrDefault("public_query", ""), parsePage(state), isPublicOwnOnly(state));
                });
                return true;
            }
            default -> {
            }
        }

        if (state.getEntityUuid() == null) {
            return false;
        }

        manager.readSnapshot(state.getEntityUuid(), snapshot -> {
            if (snapshot == null) {
                runOnPlayer(player, () -> player.sendMessage("该实体已不存在。"));
                return;
            }

            switch (awaiting) {
                case "media_url", "channel_media_url" -> playbackController.updateMediaUrl(state.getEntityUuid(), input,
                        success -> runOnPlayer(player, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                player.sendMessage("设置播放 URL 失败。");
                                return;
                            }
                            reopenPage(player, state.getType(), state.getEntityUuid());
                        }));
                case "remote_media_url" -> {
                    var binding = manager.getChannelService().resolveBinding(snapshot);
                    var channelState = manager.getChannelService().ensureChannelState(binding.channelId());
                    if (!manager.getChannelService().canControlChannelPlayback(player, channelState)) {
                        runOnPlayer(player, () -> player.sendMessage("该频道为私有频道，只有创建者或 OP 可以通过遥控器修改当前媒体。"));
                        return;
                    }
                    playbackController.updateMediaUrlAsCurrentOnly(state.getEntityUuid(), input,
                            success -> runOnPlayer(player, () -> {
                                if (!Boolean.TRUE.equals(success)) {
                                    player.sendMessage("设置当前媒体失败。");
                                    return;
                                }
                                reopenPage(player, state.getType(), state.getEntityUuid());
                            }));
                }
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
                case "channel_prepend" -> playbackController.prependPlaylistItem(state.getEntityUuid(), input,
                        success -> runOnPlayer(player, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                player.sendMessage("首加播放项失败。");
                                return;
                            }
                            reopenPage(player, state.getType(), state.getEntityUuid());
                        }));
                case "channel_append" -> playbackController.appendPlaylistItem(state.getEntityUuid(), input,
                        success -> runOnPlayer(player, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                player.sendMessage("尾加播放项失败。");
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
        if (closed) {
            return;
        }
        if (type == GuiType.CHANNEL_MENU && uuid == null) {
            var guiState = getState(player);
            String channelId = guiState != null ? guiState.getTemp().get("channel_id") : null;
            if (channelId != null) {
                runOnPlayer(player, () -> openChannelMenu(player, channelId, null));
                return;
            }
        }
        manager.readSnapshot(uuid, snapshot -> {
            if (snapshot == null) {
                return;
            }
            runOnPlayer(player, () -> {
                switch (type) {
                    case PERIPHERAL_LIST -> openPeripheralList(player, snapshot);
                    case WORLD_TRANSFORM -> openWorldTransform(player, snapshot);
                    case CHANNEL_MENU -> openChannelMenu(player, snapshot);
                    case REMOTE_MENU -> openRemoteMenu(player, snapshot);
                    case PUBLIC_CHANNEL_LIST -> openPublicChannelList(player, uuid, getState(player) != null ? getState(player).getTemp().getOrDefault("public_query", "") : "", getState(player) != null ? parsePage(getState(player)) : 0, getState(player) != null && isPublicOwnOnly(getState(player)));
                    case PUBLIC_CHANNEL_CREATE -> openPublicChannelCreate(player, uuid, "", "", "", 0, false);
                    case NEARBY_PLAYER_LIST -> openNearbyPlayerList(player, getNearbyPage(getState(player)));
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
        if (closed) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> {
            if (closed) {
                return;
            }
            task.run();
        }, null);
    }

    public static int parsePage(GuiState state) {
        try {
            return Integer.parseInt(state.getTemp().getOrDefault("public_page", "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isPublicOwnOnly(GuiState state) {
        return Boolean.parseBoolean(state.getTemp().getOrDefault("public_own_only", "false"));
    }

    public static int getNearbyPage(GuiState state) {
        return getStateInt(state, NEARBY_PAGE_KEY);
    }

    public static UUID getNearbySlotUuid(GuiState state, int slot) {
        if (state == null) {
            return null;
        }
        String value = state.getTemp().get(NEARBY_SLOT_UUID_PREFIX + slot);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int getStateInt(GuiState state, String key) {
        if (state == null) {
            return 0;
        }
        try {
            return Integer.parseInt(state.getTemp().getOrDefault(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String summarizePublicChannelName(ChannelRuntimeState state) {
        return summarize(fallback(state.getChannelName(), state.getChannelId()));
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String summarize(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return "未设置";
        }
        return mediaUrl.length() > 28 ? mediaUrl.substring(0, 25) + "..." : mediaUrl;
    }

    private static String currentPlaylistLabel(ChannelRuntimeState state) {
        if (state.getPlaylist().isEmpty()) {
            return "空列表";
        }
        int cursor = state.getNormalizedPlaylistCursor();
        return "#" + (cursor + 1) + " " + summarize(state.getPlaylist().get(cursor).mediaUrl());
    }

    private static String formatPlayOrderMode(ChannelRuntimeState state) {
        return switch (state.getPlayOrderMode()) {
            case SEQUENTIAL -> "列表播放";
            case SHUFFLE -> "随机";
            case LOOP_ALL -> "列表循环";
            case LOOP_ONE -> "当前媒体循环";
            case CURRENT_ONLY -> "播完当前停止";
        };
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
