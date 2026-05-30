package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.UUID;

public class PublicChannelCreatePage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PUBLIC_CHANNEL_CREATE; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("创建公共频道"); }

    @Override
    public Material icon() { return Material.ANVIL; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        String name = entry.getState("public_channel_name", "");
        String desc = entry.getState("public_channel_description", "");

        var inv = createInventory(entry);
        inv.setItem(20, item(Material.NAME_TAG, "设置频道名称",
                "当前: " + (name.isBlank() ? "未设置" : name)));
        inv.setItem(22, item(Material.WRITABLE_BOOK, "设置频道介绍",
                "当前: " + (desc.isBlank() ? "未设置" : desc)));
        inv.setItem(24, item(Material.EMERALD_BLOCK, "确认创建"));
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
            case 20 -> {
                context.requestInput(player, "请输入公共频道名称。", "public_channel_name");
            }
            case 22 -> {
                context.requestInput(player, "请输入公共频道介绍。", "public_channel_description");
            }
            case 24 -> {
                if (!player.hasPermission("mtv.channel.create")) {
                    player.sendMessage("你没有权限创建公共频道。需要权限: mtv.channel.create");
                    return true;
                }
                String name = entry.getState("public_channel_name", "");
                String desc = entry.getState("public_channel_description", "");
                var created = context.manager().getChannelService()
                        .createPublicChannel(player, name, desc);
                if (created == null) {
                    String normalizedName = name == null ? "" : name.trim();
                    if (normalizedName.isBlank()) {
                        player.sendMessage("创建公共频道失败，请先填写有效名称。");
                    } else {
                        player.sendMessage("创建公共频道失败，请检查是否拥有权限: mtv.channel.create");
                    }
                    return true;
                }
                var st = context.newState();
                st.put("channel_id", created.getChannelId());
                st.put(MtvGui.PUBLIC_QUERY_KEY, query);
                st.put(MtvGui.PUBLIC_PAGE_KEY, Integer.toString(page));
                st.put(MtvGui.PUBLIC_OWN_ONLY_KEY, Boolean.toString(ownOnly));
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_MANAGE,
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
        String input = message.trim();

        switch (awaiting) {
            case "public_channel_name" -> {
                entry.putState("public_channel_name", input);
                context.runOnPlayer(player, () -> context.refresh(player));
            }
            case "public_channel_description" -> {
                entry.putState("public_channel_description", input);
                context.runOnPlayer(player, () -> context.refresh(player));
            }
            default -> { return false; }
        }
        return true;
    }
}
