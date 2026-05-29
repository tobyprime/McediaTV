package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class PublicChannelManagePage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PUBLIC_CHANNEL_MANAGE; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("公共频道管理"); }

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

        boolean canManage = context.manager().getChannelService().canManagePublicChannel(player, channel);

        var inv = createInventory(entry);
        inv.setItem(10, item(Material.PLAYER_HEAD, "创建者",
                MtvGui.fallback(channel.getCreatorName(), "未知")));
        inv.setItem(11, item(Material.WRITABLE_BOOK, "频道介绍",
                MtvGui.fallback(channel.getDescription(), "无")));
        inv.setItem(12, item(Material.ENDER_EYE, "当前观看人数",
                Integer.toString(context.manager().getChannelService().getAudienceCount(channelId))));
        inv.setItem(13, item(Material.NAME_TAG, "频道 ID", channelId));
        inv.setItem(14, item(Material.JUKEBOX, "频道控制",
                entityUuid != null ? "打开当前播放器的频道控制页" : "打开附近绑定此频道的播放器控制页",
                canManage ? "你可以编辑此频道" : "你当前只能只读查看"));

        if (canManage) {
            inv.setItem(15, item(Material.NAME_TAG, "编辑频道名称"));
            inv.setItem(16, item(Material.WRITABLE_BOOK, "编辑频道介绍"));
            inv.setItem(17, item(channel.isPublicControl() ? Material.LIME_DYE : Material.GRAY_DYE,
                    "播放权限: " + (channel.isPublicControl() ? "公开" : "私有"),
                    "公开: 所有人可控制播放",
                    "私有: 仅创建者和 OP 可控制播放"));
            inv.setItem(24, item(Material.TNT, "删除公共频道"));
        } else {
            inv.setItem(15, item(Material.BARRIER, "只读",
                    "只有创建者或 OP 可以管理该频道"));
        }
        inv.setItem(49, item(Material.ARROW, "返回列表"));
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
                && context.manager().getChannelService().canManagePublicChannel(player, channel);

        // Pass through state for list navigation
        String query = entry.getState(MtvGui.PUBLIC_QUERY_KEY, "");
        int page = MtvGui.parsePage(entry);
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);

        switch (slot) {
            case 14 -> openChannelControl(player, context, entityUuid, channelId);
            case 15 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或 OP 可以编辑该公共频道。");
                    return true;
                }
                context.requestInput(player, "请输入新的公共频道名称。", "public_channel_edit_name");
            }
            case 16 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或 OP 可以编辑该公共频道。");
                    return true;
                }
                context.requestInput(player, "请输入新的公共频道介绍。", "public_channel_edit_description");
            }
            case 17 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或 OP 可以修改该公共频道的播放权限。");
                    return true;
                }
                if (channel == null) return true;
                boolean newPublicControl = !channel.isPublicControl();
                boolean success = context.manager().getChannelService()
                        .setPublicControl(player, channelId, newPublicControl);
                if (success) context.delay(player, () -> context.refresh(player));
            }
            case 24 -> {
                if (!canManage) {
                    player.sendMessage("只有创建者或 OP 可以删除该公共频道。");
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
            case 49 -> {
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
                var binding = context.manager().getChannelService().resolveBinding(snapshot);
                if (channelId.equals(binding.channelId())) {
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, entityUuid);
                } else {
                    var st = context.newState();
                    st.put("channel_id", channelId);
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, null, null, st);
                }
            });
        } else {
            var st = context.newState();
            st.put("channel_id", channelId);
            context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, null, null, st);
        }
    }

}
