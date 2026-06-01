package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * 频道详情页 — 展示公共频道的详细信息与操作入口。
 * <p>
 *   布局参考 {@link PlayerMenuPage} 的三段式风格：
 *   Row 1 (9-17) 频道基本信息、Row 3 (27-35) 操作区、Row 5 (45-53) 权限/危险。
 *   导航通过 {@link #setupTitleBar} 统一管理，不再放置单独的"返回列表"按钮。
 * </p>
 */
public class PublicChannelManagePage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PUBLIC_CHANNEL_MANAGE; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("频道详情"); }

    @Override
    public Material icon() { return Material.BOOK; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID entityUuid = entry.getEntityUuid();
        String channelId = entry.getState("channel_id", "");
        if (channelId.isBlank()) { player.closeInventory(); return; }

        var channel = context.manager().getChannelService().getPublicChannel(channelId);
        if (channel == null) {
            player.closeInventory();
            player.sendMessage("该公共频道不存在。");
            return;
        }

        boolean canManage = context.manager().getChannelService()
                .canManagePublicChannel(player, channel);

        var inv = createInventory(entry);

        // ── Row 1 (9-17): 频道详情 ──
        inv.setItem(10, item(Material.NAME_TAG,
                "§e" + MtvGui.fallback(channel.getChannelName(), "未命名频道"),
                "§7ID: §f" + channelId));

        inv.setItem(12, item(Material.PLAYER_HEAD,
                "§b创建者: " + MtvGui.fallback(channel.getCreatorName(), "未知")));

        inv.setItem(14, item(Material.WRITABLE_BOOK,
                "§d频道介绍",
                "§7" + MtvGui.fallback(channel.getDescription(), "无简介")));

        inv.setItem(16, item(Material.ENDER_EYE,
                "§a👁 观看人数",
                "§7当前: §f" + context.manager().getChannelService()
                        .getAudienceCount(channelId)));

        // ── Row 3 (27-35): 操作区 ──
        inv.setItem(29, item(Material.JUKEBOX,
                "§6📻 频道控制",
                entityUuid != null
                        ? "打开当前播放器的频道控制页"
                        : "打开频道详细控制页",
                canManage ? "§7你可编辑此频道" : "§7§o你当前只能只读查看"));

        if (canManage) {
            inv.setItem(31, item(Material.NAME_TAG,
                    "§e✎ 编辑频道名称"));
            inv.setItem(33, item(Material.WRITABLE_BOOK,
                    "§d✎ 编辑频道介绍"));
        }

        // ── Row 5 (45-53): 权限 / 危险操作 ──
        if (canManage) {
            inv.setItem(47, item(channel.isPublicControl()
                            ? Material.LIME_DYE : Material.GRAY_DYE,
                    "§f播放权限: " + (channel.isPublicControl()
                            ? "§a公开" : "§c私有"),
                    "§7公开: 所有人可控制播放",
                    "§7私有: 仅创建者和拥有频道控制权限的玩家可控制播放",
                    "§8点击切换"));

            inv.setItem(53, item(Material.TNT,
                    "§c☠ 删除此频道",
                    "§4⚠ 该操作不可撤销！",
                    "§7将永久移除该频道及其所有绑定"));
        } else {
            inv.setItem(49, item(Material.BARRIER,
                    "§c⛔ 只读模式",
                    "§7只有创建者或拥有频道管理权限的玩家可以管理该频道"));
        }

        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID entityUuid = entry.getEntityUuid();
        String channelId = entry.getState("channel_id", "");
        if (channelId.isBlank()) return false;

        var channel = context.manager().getChannelService().getPublicChannel(channelId);
        boolean canManage = channel != null
                && context.manager().getChannelService()
                        .canManagePublicChannel(player, channel);

        // Pass through state for list navigation (used on delete)
        String query = entry.getState(MtvGui.PUBLIC_QUERY_KEY, "");
        int page = MtvGui.parsePage(entry);
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);

        switch (slot) {
            // ── Row 3: 频道控制 ──
            case 29 -> openChannelControl(player, context, entityUuid, channelId);

            // ── Row 3: 编辑名称（可管理） ──
            case 31 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或拥有频道管理权限的玩家可以编辑该公共频道。");
                    return true;
                }
                context.requestInput(player,
                        "请输入新的公共频道名称。", "public_channel_edit_name");
            }

            // ── Row 3: 编辑介绍（可管理） ──
            case 33 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或拥有频道管理权限的玩家可以编辑该公共频道。");
                    return true;
                }
                context.requestInput(player,
                        "请输入新的公共频道介绍。", "public_channel_edit_description");
            }

            // ── Row 5: 播放权限切换（可管理） ──
            case 47 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或拥有频道管理权限的玩家可以修改该公共频道的播放权限。");
                    return true;
                }
                if (channel == null) return true;
                boolean newPublicControl = !channel.isPublicControl();
                boolean success = context.manager().getChannelService()
                        .setPublicControl(player, channelId, newPublicControl);
                if (success) context.delay(player, () -> context.refresh(player));
            }

            // ── Row 5: 删除频道（可管理） ──
            case 53 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或拥有频道管理权限的玩家可以删除该公共频道。");
                    return true;
                }
                boolean success = context.manager().getChannelService()
                        .deletePublicChannel(player, channelId);
                if (!success) {
                    player.sendMessage("删除公共频道失败，可能仍有播放器绑定该频道。");
                    return true;
                }
                var st = MtvGui.publicChannelState(query, page, ownOnly);
                st.put("channel_id", channelId);
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST,
                        entityUuid, null, st);
            }

            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        if (awaiting == null) return false;
        String channelId = entry.getState("channel_id", "");
        UUID entityUuid = entry.getEntityUuid();
        String input = message.trim();
        String query = entry.getState(MtvGui.PUBLIC_QUERY_KEY, "");
        int page = MtvGui.parsePage(entry);
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);

        switch (awaiting) {
            case "public_channel_edit_name" -> {
                boolean success = context.manager().getChannelService()
                        .updatePublicChannelName(player, channelId, input);
                context.runOnPlayer(player, () -> {
                    if (!success) {
                        player.sendMessage("更新频道名称失败。");
                        return;
                    }
                    context.refresh(player);
                });
            }
            case "public_channel_edit_description" -> {
                boolean success = context.manager().getChannelService()
                        .updatePublicChannelDescription(player, channelId, input);
                context.runOnPlayer(player, () -> {
                    if (!success) {
                        player.sendMessage("更新频道介绍失败。");
                        return;
                    }
                    context.refresh(player);
                });
            }
            default -> { return false; }
        }
        return true;
    }

    private void openChannelControl(Player player, GuiPageContext context,
                                     UUID entityUuid, String channelId) {
        if (entityUuid != null) {
            context.read(player, entityUuid, snapshot -> {
                var binding = context.manager().getChannelService()
                        .resolveBinding(snapshot);
                if (channelId.equals(binding.channelId())) {
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU,
                            entityUuid);
                } else {
                    var st = context.newState();
                    st.put("channel_id", channelId);
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU,
                            null, null, st);
                }
            });
        } else {
            var st = context.newState();
            st.put("channel_id", channelId);
            context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU,
                    null, null, st);
        }
    }
}
