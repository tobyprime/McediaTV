package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.UUID;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;

public class PublicChannelListPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PUBLIC_CHANNEL_LIST; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("公共频道"); }

    @Override
    public Material icon() { return Material.BOOK; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        String query = entry.getState(MtvGui.PUBLIC_QUERY_KEY, "");
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);
        var results = context.manager().getChannelService()
                .searchPublicChannels(query, player.getUniqueId(), ownOnly);
        int requestedPage = MtvGui.parsePage(entry);
        int totalPages = Math.max(1, (results.size() + MtvGui.PUBLIC_CHANNEL_SLOTS.length - 1)
                / MtvGui.PUBLIC_CHANNEL_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        var inv = createInventory(entry);
        inv.setItem(45, item(Material.ARROW, "上一页"));
        inv.setItem(46, item(ownOnly ? Material.LIME_DYE : Material.GRAY_DYE,
                ownOnly ? "只看我的频道" : "查看全部频道"));
        inv.setItem(47, item(Material.OAK_SIGN, "输入搜索词"));
        inv.setItem(48, item(Material.BARRIER, "清空搜索"));
        inv.setItem(49, item(Material.ANVIL, "创建公共频道"));
        inv.setItem(53, item(Material.ARROW, "下一页"));

        int start = page * MtvGui.PUBLIC_CHANNEL_SLOTS.length;
        for (int i = 0; i < MtvGui.PUBLIC_CHANNEL_SLOTS.length && start + i < results.size(); i++) {
            var channelState = results.get(start + i);
            inv.setItem(MtvGui.PUBLIC_CHANNEL_SLOTS[i], item(Material.PAPER,
                    MtvGui.summarizePublicChannelName(channelState),
                    "创建者: " + MtvGui.fallback(channelState.getCreatorName(), "未知"),
                    "简介: " + MtvGui.fallback(MtvGui.summarize(channelState.getDescription()), "无"),
                    "观看中: " + context.manager().getChannelService().getAudienceCount(channelState.getChannelId()),
                    entry.getEntityUuid() != null ? "左键绑定当前播放器 / 右键管理" : "点击管理"));
        }

        entry.putState(MtvGui.PUBLIC_QUERY_KEY, query);
        entry.putState(MtvGui.PUBLIC_PAGE_KEY, Integer.toString(page));
        entry.putState(MtvGui.PUBLIC_OWN_ONLY_KEY, Boolean.toString(ownOnly));
        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID entityUuid = entry.getEntityUuid();
        String query = entry.getState(MtvGui.PUBLIC_QUERY_KEY, "");
        int page = MtvGui.parsePage(entry);
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);

        switch (slot) {
            case 45 -> {
                var st = MtvGui.publicChannelState(query, Math.max(0, page - 1), ownOnly);
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, entityUuid, null, st);
            }
            case 46 -> {
                var st = MtvGui.publicChannelState(query, 0, !ownOnly);
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, entityUuid, null, st);
            }
            case 47 -> {
                context.requestInput(player, "请输入搜索关键词。可按频道名、介绍、创建者搜索。", MtvGui.AWAITING_PUBLIC_CHANNEL_SEARCH);
            }
            case 48 -> {
                var st = MtvGui.publicChannelState("", 0, ownOnly);
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, entityUuid, null, st);
            }
            case 49 -> {
                if (!player.hasPermission("mcedia.mtv.channel.create")) {
                    player.sendMessage("你没有权限创建公共频道。");
                    return true;
                }
                var st = MtvGui.publicChannelState(query, page, ownOnly);
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_CREATE, entityUuid, null, st);
            }
            case 53 -> {
                var st = MtvGui.publicChannelState(query, page + 1, ownOnly);
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, entityUuid, null, st);
            }
            default -> {
                int localIndex = GuiPage.indexOf(MtvGui.PUBLIC_CHANNEL_SLOTS, slot);
                if (localIndex < 0) return false;
                var results = context.manager().getChannelService()
                        .searchPublicChannels(query, player.getUniqueId(), ownOnly);
                int globalIndex = page * MtvGui.PUBLIC_CHANNEL_SLOTS.length + localIndex;
                if (globalIndex < 0 || globalIndex >= results.size()) return false;
                var channel = results.get(globalIndex);
                if (entityUuid != null && !rightClick) {
                    context.manager().updateChannelBinding(entityUuid,
                            MtvChannelBinding.broadcast(channel.getChannelId()),
                            success -> context.delay(player, () -> {
                                if (!Boolean.TRUE.equals(success)) {
                                    player.sendMessage("绑定公共频道失败。");
                                    return;
                                }
                                context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, entityUuid);
                            }));
                } else {
                    var st = MtvGui.publicChannelState(query, page, ownOnly);
                    st.put("channel_id", channel.getChannelId());
                    context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_MANAGE,
                            entityUuid, null, st);
                }
            }
        }
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        if (!MtvGui.AWAITING_PUBLIC_CHANNEL_SEARCH.equals(
                entry.getState().get(MtvGui.AWAITING_KEY))) {
            return false;
        }
        // awaiting key already consumed, but we still check it for routing
        String query = message.trim();
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);
        context.runOnPlayer(player, () -> {
            var st = MtvGui.publicChannelState(query, 0, ownOnly);
            context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST,
                    entry.getEntityUuid(), null, st);
        });
        return true;
    }

}
