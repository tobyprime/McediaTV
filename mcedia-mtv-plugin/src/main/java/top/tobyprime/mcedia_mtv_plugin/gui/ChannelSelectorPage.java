package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import top.tobyprime.mcedia_mtv_plugin.channel.PublicChannelSort;

/**
 * A generic channel picker — shows public channel list with search/sort/pagination.
 * On click, stores the selected channel ID in {@code selected_channel_id} and
 * navigates to the page type specified in {@code selector_return}.
 * <p>
 * Usage from any page:
 * {@code
 *   var st = context.newState();
 *   st.put("selector_return", "HUD_MENU");  // MtvGui.GuiType name
 *   context.navigateTo(player, MtvGui.GuiType.CHANNEL_SELECTOR, null, null, st);
 * }
 */
public class ChannelSelectorPage extends GuiPage {
    public static final String RETURN_KEY = "selector_return";
    public static final String SELECTED_KEY = "selected_channel_id";

    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.CHANNEL_SELECTOR; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("选择频道"); }

    @Override
    public Material icon() { return Material.COMPASS; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        String query = entry.getState(MtvGui.PUBLIC_QUERY_KEY, "");
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);
        var sort = MtvGui.parsePublicSort(entry);
        var results = context.manager().getChannelService()
                .searchPublicChannels(query, player.getUniqueId(), ownOnly, sort);
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
        inv.setItem(50, item(Material.COMPARATOR,
                "排序: " + sort.displayName(),
                "点击切换排序方式"));
        inv.setItem(53, item(Material.ARROW, "下一页"));

        int start = page * MtvGui.PUBLIC_CHANNEL_SLOTS.length;
        for (int i = 0; i < MtvGui.PUBLIC_CHANNEL_SLOTS.length && start + i < results.size(); i++) {
            var channelState = results.get(start + i);
            inv.setItem(MtvGui.PUBLIC_CHANNEL_SLOTS[i], item(Material.PAPER,
                    MtvGui.summarizePublicChannelName(channelState),
                    "创建者: " + MtvGui.fallback(channelState.getCreatorName(), "未知"),
                    "简介: " + MtvGui.fallback(MtvGui.summarize(channelState.getDescription()), "无"),
                    "观看中: " + context.manager().getChannelService().getAudienceCount(channelState.getChannelId()),
                    "点击选择"));
        }

        entry.putState(MtvGui.PUBLIC_QUERY_KEY, query);
        entry.putState(MtvGui.PUBLIC_PAGE_KEY, Integer.toString(page));
        entry.putState(MtvGui.PUBLIC_OWN_ONLY_KEY, Boolean.toString(ownOnly));
        entry.putState(MtvGui.PUBLIC_SORT_KEY, sort.name());
        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        String query = entry.getState(MtvGui.PUBLIC_QUERY_KEY, "");
        int page = MtvGui.parsePage(entry);
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);
        var sort = MtvGui.parsePublicSort(entry);
        String returnType = entry.getState(RETURN_KEY, "");

        switch (slot) {
            case 45 -> navigateBack(context, player, entry, query, Math.max(0, page - 1), ownOnly, sort);
            case 46 -> navigateBack(context, player, entry, query, 0, !ownOnly, sort);
            case 47 -> context.requestInput(player, "请输入搜索关键词。", MtvGui.AWAITING_PUBLIC_CHANNEL_SEARCH);
            case 48 -> navigateBack(context, player, entry, "", 0, ownOnly, sort);
            case 50 -> navigateBack(context, player, entry, query, 0, ownOnly, sort.next());
            case 53 -> navigateBack(context, player, entry, query, page + 1, ownOnly, sort);
            default -> {
                int localIndex = GuiPage.indexOf(MtvGui.PUBLIC_CHANNEL_SLOTS, slot);
                if (localIndex < 0) return false;
                var results = context.manager().getChannelService()
                        .searchPublicChannels(query, player.getUniqueId(), ownOnly, sort);
                int globalIndex = page * MtvGui.PUBLIC_CHANNEL_SLOTS.length + localIndex;
                if (globalIndex < 0 || globalIndex >= results.size()) return false;
                var channel = results.get(globalIndex);

                // Store selection and return to caller
                var returnState = context.newState();
                returnState.put(SELECTED_KEY, channel.getChannelId());
                returnState.put("selected_channel_name", channel.getChannelName());

                MtvGui.GuiType returnGui;
                try {
                    returnGui = MtvGui.GuiType.valueOf(returnType);
                } catch (Exception e) {
                    return false;
                }
                context.navigateTo(player, returnGui, null, null, returnState);
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
        String query = message.trim();
        boolean ownOnly = MtvGui.isPublicOwnOnly(entry);
        var sort = MtvGui.parsePublicSort(entry);
        context.runOnPlayer(player, () -> {
            var st = context.newState();
            st.putAll(entry.getState());
            st.put(MtvGui.PUBLIC_QUERY_KEY, query);
            st.put(MtvGui.PUBLIC_PAGE_KEY, "0");
            st.put(MtvGui.PUBLIC_OWN_ONLY_KEY, Boolean.toString(ownOnly));
            st.put(MtvGui.PUBLIC_SORT_KEY, sort.name());
            context.navigateTo(player, MtvGui.GuiType.CHANNEL_SELECTOR, null, null, st);
        });
        return true;
    }

    private static void navigateBack(GuiPageContext context, Player player,
                                      PageEntry entry, String query, int page,
                                      boolean ownOnly, PublicChannelSort sort) {
        var st = context.newState();
        st.putAll(entry.getState());
        st.put(MtvGui.PUBLIC_QUERY_KEY, query);
        st.put(MtvGui.PUBLIC_PAGE_KEY, Integer.toString(page));
        st.put(MtvGui.PUBLIC_OWN_ONLY_KEY, Boolean.toString(ownOnly));
        st.put(MtvGui.PUBLIC_SORT_KEY, sort.name());
        context.navigateTo(player, MtvGui.GuiType.CHANNEL_SELECTOR, null, null, st);
    }
}
