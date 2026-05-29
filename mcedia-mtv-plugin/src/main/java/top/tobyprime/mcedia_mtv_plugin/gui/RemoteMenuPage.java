package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.UUID;

public class RemoteMenuPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.REMOTE_MENU; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("遥控器"); }

    @Override
    public Material icon() { return Material.IRON_DOOR; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) {
            // No entity UUID — find the nearest player
            context.selector().findNearbyAsync(player, MtvGui.NEARBY_RANGE,
                    candidates -> context.runOnPlayer(player, () -> {
                        if (candidates.isEmpty()) {
                            player.sendMessage("附近 " + (int) MtvGui.NEARBY_RANGE + " 米内没有 MTV 播放器。");
                            context.navigateTo(player, MtvGui.GuiType.MAIN_MENU);
                            return;
                        }
                        var entry2 = new PageEntry(MtvGui.GuiType.REMOTE_MENU,
                                candidates.get(0).getUuid());
                        open(player, context, entry2);
                    }));
            return;
        }
        // Have entity UUID — render
        context.read(player, uuid, snapshot -> {
            var binding = context.manager().getChannelService().resolveBinding(snapshot);
            var publicChannel = binding.isBroadcast()
                    ? context.manager().getChannelService().getPublicChannel(binding.channelId())
                    : null;
            var channelState = context.manager().getChannelService()
                    .ensureChannelState(binding.channelId());
            var playState = channelState != null ? channelState.getPlayState() : null;

            var inv = createInventory(entry);
            inv.setItem(10, item(snapshot.isPowered() ? Material.LIME_DYE : Material.GRAY_DYE,
                    "电源: " + (snapshot.isPowered() ? "开" : "关"), "点击切换待机状态"));
            inv.setItem(11, item(Material.NOTE_BLOCK,
                    "总音量: " + String.format("%.2f", snapshot.getMasterVolume()),
                    "左键 -0.1 / 右键 +0.1", "潜行改动 0.25"));
            inv.setItem(12, item(Material.STONE_BUTTON, "后退 1 秒", "潜行点击后退 10 秒"));
            inv.setItem(13, item(Material.MUSIC_DISC_CAT, "设置当前媒体", "切到该视频，并改为播完当前停止",
                    binding.isBroadcast() ? "公共频道需要创建者或 OP" : "会覆盖当前列表"));
            inv.setItem(14, item(Material.STONE_BUTTON, "前进 1 秒", "潜行点击前进 10 秒"));
            inv.setItem(20, item(Material.ITEM_FRAME, "播放器入口", "打开当前最近播放器页"));
            inv.setItem(22, item(Material.SPYGLASS, "刷新目标", "重新选择最近的 MTV"));
            inv.setItem(24, item(Material.JUKEBOX, "频道入口", "打开当前播放器绑定的频道页"));
            setupTitleBar(inv, nav, entry);
            openInventory(player, inv);
        });
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return false;
        context.read(player, uuid, snap -> {
            if (snap == null) return;
            switch (slot) {
                case 10 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setPowered(uuid, !snap.isPowered(), done));
                case 11 -> {
                    float delta = shiftClick ? (rightClick ? 0.25F : -0.25F)
                                             : (rightClick ? 0.1F : -0.1F);
                    float volume = Math.max(0.0F, Math.min(1.0F, snap.getMasterVolume() + delta));
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setMasterVolume(uuid, volume, done));
                }
                case 12 -> {
                    if (!canManage(player, context, snap)) return;
                    long delta = shiftClick ? -10_000_000L : -1_000_000L;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, delta, done));
                }
                case 13 -> {
                    if (!canManage(player, context, snap)) return;
                    player.closeInventory();
                    player.sendMessage(MtvGui.MEDIA_INPUT_MESSAGE);
                    context.setAwaitingInput(player, "remote_media_url");
                }
                case 14 -> {
                    if (!canManage(player, context, snap)) return;
                    long delta = shiftClick ? 10_000_000L : 1_000_000L;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, delta, done));
                }
                case 20 -> context.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, uuid);
                case 22 -> {
                    // Re-find nearest player
                    context.selector().findNearbyAsync(player, MtvGui.NEARBY_RANGE,
                            candidates -> context.runOnPlayer(player, () -> {
                                if (candidates.isEmpty()) {
                                    player.sendMessage("附近 " + (int) MtvGui.NEARBY_RANGE + " 米内没有 MTV 播放器。");
                                    return;
                                }
                                context.navigateTo(player, MtvGui.GuiType.REMOTE_MENU,
                                        candidates.get(0).getUuid());
                            }));
                }
                case 24 -> {
                    var binding = context.manager().getChannelService().resolveBinding(snap);
                    var st = context.newState();
                    st.put("channel_id", binding.channelId());
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, uuid, null, st);
                }
            }
        });
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        if (!"remote_media_url".equals(awaiting)) return false;

        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return true;
        String input = message.trim();
        context.read(player, uuid, snap -> {
            if (snap == null) return;
            var binding = context.manager().getChannelService().resolveBinding(snap);
            var channelState = context.manager().getChannelService()
                    .ensureChannelState(binding.channelId());
            if (!context.manager().getChannelService().canControlChannelPlayback(player, channelState)) {
                context.runOnPlayer(player, () ->
                        player.sendMessage("该频道为私有频道，只有创建者或 OP 可以通过遥控器修改当前媒体。"));
                return;
            }
            context.playbackController().updateMediaUrlAsCurrentOnly(uuid, input,
                    success -> context.runOnPlayer(player, () -> {
                        if (!Boolean.TRUE.equals(success)) {
                            player.sendMessage("设置当前媒体失败。");
                            return;
                        }
                        context.refresh(player);
                    }));
        });
        return true;
    }

    private static boolean canManage(Player player, GuiPageContext context,
                                     ManagedMtvPlayer snapshot) {
        var binding = context.manager().getChannelService().resolveBinding(snapshot);
        var state = context.manager().getChannelService().ensureChannelState(binding.channelId());
        if (context.manager().getChannelService().canControlChannelPlayback(player, state)) {
            return true;
        }
        player.sendMessage("该频道为私有频道，只有创建者或 OP 可以通过遥控器控制播放。");
        return false;
    }
}
