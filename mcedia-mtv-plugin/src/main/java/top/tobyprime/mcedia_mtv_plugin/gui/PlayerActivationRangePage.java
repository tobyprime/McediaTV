package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;

import java.util.Locale;
import java.util.UUID;

public class PlayerActivationRangePage extends GuiPage {
    private static final String AWAITING_SET_RANGE = "set_activation_range";

    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PLAYER_ACTIVATION_RANGE; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("激活范围"); }

    @Override
    public Material icon() { return Material.SPYGLASS; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) { player.closeInventory(); return; }
        context.read(player, uuid, snapshot -> {
            if (snapshot == null) return;
            var inv = createInventory(entry);
            float range = snapshot.getMaxActiveRange();
            String currentRange = formatRange(range);

            inv.setItem(11, item(Material.RED_CONCRETE,
                    "§c− 5 格",
                    "§7当前: §f" + currentRange,
                    "§7点击减少 5 格"));
            inv.setItem(12, item(Material.ORANGE_CONCRETE,
                    "§6− 1 格",
                    "§7当前: §f" + currentRange,
                    "§7点击减少 1 格"));
            inv.setItem(13, item(Material.SPYGLASS,
                    "§b📏 当前范围",
                    "§7当前: §f" + currentRange,
                    "§7超出该距离时，此客户端将临时关闭该播放器",
                    "§7回到范围内会自动恢复"));
            inv.setItem(14, item(Material.LIME_CONCRETE,
                    "§a+ 1 格",
                    "§7当前: §f" + currentRange,
                    "§7点击增加 1 格"));
            inv.setItem(15, item(Material.GREEN_CONCRETE,
                    "§a+ 5 格",
                    "§7当前: §f" + currentRange,
                    "§7点击增加 5 格"));

            inv.setItem(31, item(Material.WRITABLE_BOOK,
                    "§e✎ 精确设置",
                    "§7当前: §f" + currentRange,
                    "§7点击后在聊天栏输入数值",
                    "§7输入 0 / off / none 表示无限制"));
            inv.setItem(33, item(Material.BARRIER,
                    "§c∞ 设为无限制",
                    "§7取消距离限制",
                    "§7播放器仅受正常开关机状态控制"));

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
            if (!MtvPeripheralController.canEdit(player, snap)) {
                player.sendMessage("该 MTV 播放器为私有，只有创建者或拥有 mtv.player.edit.others 权限的玩家可以编辑。");
                return;
            }

            switch (slot) {
                case 11 -> updateRange(player, context, uuid, snap.getMaxActiveRange() - 5.0F);
                case 12 -> updateRange(player, context, uuid, snap.getMaxActiveRange() - 1.0F);
                case 14 -> updateRange(player, context, uuid, snap.getMaxActiveRange() + 1.0F);
                case 15 -> updateRange(player, context, uuid, snap.getMaxActiveRange() + 5.0F);
                case 31 -> context.requestInput(player,
                        "请输入最大激活范围，输入 0、off 或 none 表示无限制。",
                        AWAITING_SET_RANGE);
                case 33 -> updateRange(player, context, uuid, 0.0F);
                default -> {
                }
            }
        });
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                   PageEntry entry, String message) {
        if (!AWAITING_SET_RANGE.equals(entry.getState().get(MtvGui.AWAITING_KEY))) {
            return false;
        }
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return true;

        String input = message.trim();
        String normalized = input.toLowerCase(Locale.ROOT);
        if (normalized.equals("off") || normalized.equals("none") || normalized.equals("0")) {
            context.manager().setMaxActiveRange(uuid, 0.0F, success -> context.runOnPlayer(player, () -> {
                if (!Boolean.TRUE.equals(success)) {
                    player.sendMessage("设置最大激活范围失败。");
                    return;
                }
                context.refresh(player);
            }));
            return true;
        }

        final float range;
        try {
            range = Math.max(0.0F, Float.parseFloat(input));
        } catch (NumberFormatException e) {
            context.runOnPlayer(player, () -> {
                player.sendMessage("请输入有效的数字，或输入 0、off、none 表示无限制。");
                context.refresh(player);
            });
            return true;
        }

        context.manager().setMaxActiveRange(uuid, range, success -> context.runOnPlayer(player, () -> {
            if (!Boolean.TRUE.equals(success)) {
                player.sendMessage("设置最大激活范围失败。");
                return;
            }
            context.refresh(player);
        }));
        return true;
    }

    private void updateRange(Player player, GuiPageContext context, UUID uuid, float nextRange) {
        context.updateAndRefresh(player, uuid,
                done -> context.manager().setMaxActiveRange(uuid, nextRange, done));
    }

    private static String formatRange(float range) {
        return range <= 0.0F ? "无限制" : String.format("%.1f 格", range);
    }
}
