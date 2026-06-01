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
            var channelState = context.manager().getChannelService()
                    .ensureChannelState(binding.channelId());
            var playState = channelState != null ? channelState.getPlayState() : null;

            var inv = createInventory(entry);

            // ── 左栏: 电源 / 播放器 / 频道 ──

            inv.setItem(9, item(snapshot.isPowered() ? Material.LIME_DYE : Material.GRAY_DYE,
                    "§a⚡ 电源: " + (snapshot.isPowered() ? "开" : "关"), "§7点击切换待机状态"));

            inv.setItem(18, item(Material.ITEM_FRAME,
                    "§b📺 播放器设置", "§7打开当前播放器的配置页面"));

            inv.setItem(27, item(Material.JUKEBOX,
                    "§6📻 频道设置", "§7打开当前播放器的频道页面"));

            // ── 右栏: 🔊 音量 — 竖向排列 ──

            String volStr = String.format("%.2f", snapshot.getMasterVolume());
            inv.setItem(17, item(Material.GREEN_CONCRETE,
                    "§a+ §7增大音量", "§7当前: §f" + volStr, "§7点击 +0.1"));
            inv.setItem(26, item(Material.NOTE_BLOCK,
                    "§6🔊 音量: " + volStr));
            inv.setItem(35, item(Material.RED_CONCRETE,
                    "§c− §7减小音量", "§7当前: §f" + volStr, "§7点击 −0.1"));

            // ── Row 5 (45-53): ⏯️ 播放控制 — 底部居中 ──
            //     [⏮][⏪−20s][◀−5s][设置URL][▶+5s][⏩+20s][⏭]

            inv.setItem(46, item(Material.STONE_BUTTON,
                    "§b⏮ 上一个", "§7切换到上一首"));

            inv.setItem(47, item(Material.RED_STAINED_GLASS,
                    "§c⏪ −20 秒", "§7点击后退 20 秒"));

            inv.setItem(48, item(Material.RED_CONCRETE,
                    "§c◀️ −5 秒", "§7点击后退 5 秒"));

            inv.setItem(49, item(Material.MUSIC_DISC_CAT,
                    "§d📝 设置URL",
                    "§7插入到当前播放之后并立即播放"));

            inv.setItem(50, item(Material.GREEN_CONCRETE,
                    "§a▶️ +5 秒", "§7点击前进 5 秒"));

            inv.setItem(51, item(Material.GREEN_STAINED_GLASS,
                    "§a⏩ +20 秒", "§7点击前进 20 秒"));

            inv.setItem(52, item(Material.STONE_BUTTON,
                    "§b⏭ 下一个", "§7切换到下一首"));

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
                // ── Row 1: ⚡ 电源 ──
                case 9 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setPowered(uuid, !snap.isPowered(), done));

                // ── Row 2: 📺 播放器 + 右栏音量┐ ──
                case 18 -> context.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, uuid);
                // ── Row 1: 🔊 音量+ ──
                case 17 -> {
                    float step = shiftClick ? 0.25F : 0.1F;
                    float volume = Math.max(0.0F, Math.min(1.0F, snap.getMasterVolume() + step));
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setMasterVolume(uuid, volume, done));
                }

                // ── Row 3: 📻 频道 + 右栏音量┐ ──
                case 27 -> {
                    var binding = context.manager().getChannelService().resolveBinding(snap);
                    var st = context.newState();
                    st.put("channel_id", binding.channelId());
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, uuid, null, st);
                }

                // ── Row 5: ⏯️ 播放控制 (底部) ──
                case 46 -> {
                    if (!canManage(player, context, snap)) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().playPreviousManual(uuid, done));
                }
                case 47 -> {
                    if (!canManage(player, context, snap)) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, -20_000_000L, done));
                }
                case 48 -> {
                    if (!canManage(player, context, snap)) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, -5_000_000L, done));
                }
                case 49 -> {
                    if (!canManage(player, context, snap)) return;
                    player.closeInventory();
                    player.sendMessage("请输入要插入并立即播放的链接。" + MtvGui.MEDIA_INPUT_HINT + "。");
                    context.setAwaitingInput(player, "remote_set_url");
                }
                case 50 -> {
                    if (!canManage(player, context, snap)) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, 5_000_000L, done));
                }
                case 51 -> {
                    if (!canManage(player, context, snap)) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, 20_000_000L, done));
                }
                case 52 -> {
                    if (!canManage(player, context, snap)) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().playNextManual(uuid, done));
                }
                case 35 -> {
                    float step = shiftClick ? -0.25F : -0.1F;
                    float volume = Math.max(0.0F, Math.min(1.0F, snap.getMasterVolume() + step));
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setMasterVolume(uuid, volume, done));
                }
            }
        });
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return true;
        String input = message.trim();

        if ("remote_set_url".equals(awaiting)) {
            context.read(player, uuid, snap -> {
                if (snap == null) return;
                var binding = context.manager().getChannelService().resolveBinding(snap);
                var channelState = context.manager().getChannelService()
                        .ensureChannelState(binding.channelId());
                if (!context.manager().getChannelService().canControlChannelPlayback(player, channelState)) {
                    context.runOnPlayer(player, () ->
                            player.sendMessage("该频道未开启公开控制，只有创建者或拥有频道控制权限的玩家可以通过遥控器修改当前媒体。"));
                    return;
                }
                context.playbackController().insertNextAndPlay(uuid, input,
                        success -> context.runOnPlayer(player, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                player.sendMessage("设置URL失败。");
                                return;
                            }
                            context.refresh(player);
                        }));
            });
            return true;
        }

        return false;
    }

    private static boolean canManage(Player player, GuiPageContext context,
                                     ManagedMtvPlayer snapshot) {
        var binding = context.manager().getChannelService().resolveBinding(snapshot);
        var state = context.manager().getChannelService().ensureChannelState(binding.channelId());
        if (context.manager().getChannelService().canControlChannelPlayback(player, state)) {
            return true;
        }
        player.sendMessage("该频道未开启公开控制，只有创建者或拥有频道控制权限的玩家可以通过遥控器控制播放。");
        return false;
    }
}
