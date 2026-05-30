package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.UUID;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;

public class PlayerMenuPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PLAYER_MENU; }

    @Override
    public Component getTitle(PageEntry entry) {
        String name = entry.getState("player_name", "MTV");
        return Component.text("MTV: " + name);
    }

    @Override
    public Material icon() { return Material.NAME_TAG; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) { player.closeInventory(); return; }
        context.read(player, uuid, snapshot -> {
            var binding = context.manager().getChannelService().resolveBinding(snapshot);

            entry.putState("player_name", snapshot.getName());
            var inv = createInventory(entry);

            // ── Row 1 (9-17): ⚙️ 设备配置 ──

            inv.setItem(11, item(Material.NAME_TAG,
                    "§e✎ 重命名",
                    "§7当前名称: §f" + snapshot.getName(),
                    "§7点击后在聊天栏输入新名称"));

            inv.setItem(13, item(Material.ENDER_PEARL,
                    "§b⇥ 传送到位置",
                    "§7将你传送至此 MTV 播放器所在位置"));

            int screenCount = snapshot.getScreens().size();
            int speakerCount = snapshot.getSpeakers().size();
            inv.setItem(15, item(Material.GLOW_ITEM_FRAME,
                    "§d📋 外设管理",
                    "§7屏幕: §f" + screenCount + " 个  §7|  扬声器: §f" + speakerCount + " 个",
                    "§7管理连接的外接显示与音频设备"));

            // ── Row 3 (27-35): 🔧 播放器设置 ──

            inv.setItem(29, item(Material.COMPASS,
                    "§6⇕ 位置与朝向",
                    "§7调整此 MTV 的位置、偏航与俯仰角"));

            var owner = snapshot.getOwner();
            String ownerName = owner == null ? "§7未知" : "§f" + Bukkit.getOfflinePlayer(owner).getName();
            var publicIcon = snapshot.isPublic() ? Material.LIME_WOOL : Material.RED_WOOL;
            var publicText = snapshot.isPublic() ? "§a🔓 公开" : "§c🔒 私有";
            inv.setItem(31, item(publicIcon,
                    publicText,
                    "§7拥有者: " + ownerName,
                    "§7点击切换公开/私有模式",
                    snapshot.isPublic() ? "§7任何人都可以编辑此播放器" : "§7只有创建者可以编辑此播放器"));

            // ── Row 5 (45-53): 📡 频道管理 + ⚠️ 危险操作 ──

            if (binding.isBroadcast()) {
                var publicChannel = context.manager().getChannelService()
                        .getPublicChannel(binding.channelId());
                inv.setItem(47, item(Material.WRITABLE_BOOK,
                        "§d📡 查看当前频道",
                        "§7频道: §b" + MtvGui.fallback(
                                publicChannel != null ? publicChannel.getChannelName() : null,
                                binding.channelId()),
                        "§7点击查看频道详情与成员"));
            } else {
                inv.setItem(47, item(Material.JUKEBOX,
                        "§6📻 频道设置",
                        "§7管理播放列表、播放模式与频道配置"));
            }

            inv.setItem(49, item(Material.KNOWLEDGE_BOOK,
                    "§b🔗 绑定新的频道",
                    "§7搜索并绑定可用的公共 MTV 频道"));

            inv.setItem(51, item(Material.LEVER,
                    "§e⬅ 切回私有频道",
                    "§7解除公共频道绑定，恢复独立播放模式"));

            inv.setItem(53, item(Material.TNT,
                    "§c☠ 删除 MTV 播放器",
                    "§4⚠ 该操作不可撤销！",
                    "§7将永久移除该播放器及其所有外设"));

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
            var binding = context.manager().getChannelService().resolveBinding(snap);

            switch (slot) {
                // ── Row 1: 设备配置 ──
                case 11 -> {
                    if (!MtvPeripheralController.canEdit(player, snap)) return;
                    context.requestInput(player, "请输入新名称。", "rename");
                }
                case 13 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mtv.player.teleport")) return;
                    var loc = snap.toLocation();
                    if (loc == null) {
                        player.sendMessage("该 MTV 的位置无效或所在世界不存在。");
                        return;
                    }
                    player.teleportAsync(loc);
                    player.sendMessage("已传送到 MTV 播放器: " + snap.getName());
                }
                case 15 -> context.navigateTo(player, MtvGui.GuiType.PERIPHERAL_LIST, uuid);

                // ── Row 3: 播放器设置 ──
                case 29 -> context.navigateTo(player, MtvGui.GuiType.WORLD_TRANSFORM, uuid);
                case 31 -> {
                    if (!MtvPeripheralController.canEdit(player, snap)) return;
                    boolean next = !snap.isPublic();
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setPublicAsync(uuid, next, done));
                }

                // ── Row 5: 频道管理 ──
                case 47 -> {
                    if (binding.isBroadcast()) {
                        var st = context.newState();
                        st.put("channel_id", binding.channelId());
                        context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_MANAGE, uuid, null, st);
                        return;
                    }
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, uuid);
                }
                case 49 -> context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, uuid);
                case 51 -> {
                    if (!MtvPeripheralController.canEdit(player, snap)) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().updateChannelBinding(uuid, MtvChannelBinding.self(), done));
                }

                // ── Row 5: 危险操作 ──
                case 53 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mtv.player.edit")) return;
                    if (!MtvPeripheralController.canEdit(player, snap)) return;
                    player.closeInventory();
                    context.manager().deletePlayerAsync(uuid, success ->
                            context.delay(player, () -> player.sendMessage(Boolean.TRUE.equals(success)
                                    ? "已删除 MTV 播放器。"
                                    : "删除 MTV 播放器失败。")));
                }
            }
        });
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        if (awaiting == null) return false;
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return true;
        String input = message.trim();

        switch (awaiting) {
            case "rename" -> context.manager().updateName(uuid, input,
                    success -> context.runOnPlayer(player, () -> {
                        if (!Boolean.TRUE.equals(success)) {
                            player.sendMessage("重命名失败。");
                            return;
                        }
                        context.refresh(player);
                    }));
            default -> { return false; }
        }
        return true;
    }
}
