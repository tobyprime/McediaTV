package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import java.util.UUID;

public class WorldTransformPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.WORLD_TRANSFORM; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("位置与朝向"); }

    @Override
    public Material icon() { return Material.COMPASS; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) { player.closeInventory(); return; }
        context.read(player, uuid, snapshot -> {
            var inv = createInventory(entry);
            inv.setItem(10, item(Material.RED_WOOL, "X: " + String.format("%.1f", snapshot.getX()),
                    "左键 -0.5 / 右键 +0.5"));
            inv.setItem(11, item(Material.GREEN_WOOL, "Y: " + String.format("%.1f", snapshot.getY()),
                    "左键 -0.5 / 右键 +0.5"));
            inv.setItem(12, item(Material.BLUE_WOOL, "Z: " + String.format("%.1f", snapshot.getZ()),
                    "左键 -0.5 / 右键 +0.5"));
            inv.setItem(15, item(Material.LIGHTNING_ROD, "吸附到网格", "四舍五入到整数坐标"));
            inv.setItem(16, item(Material.ENDER_EYE, "传送到此处", "将实体传送到你脚下"));
            inv.setItem(19, item(Material.COMPASS, "朝向: " + String.format("%.0f", snapshot.getYaw()) + "°",
                    "左键 -5 / 右键 +5"));
            inv.setItem(20, item(Material.COMPASS, "俯仰: " + String.format("%.0f", snapshot.getPitch()) + "°",
                    "左键 -5 / 右键 +5"));
            inv.setItem(21, item(Material.STRUCTURE_VOID, "重置朝向", "恢复默认朝向"));
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
        float dir = rightClick ? 1 : -1;
        float posStep = dir * (shiftClick ? 1.0F : 0.1F);
        float rotStep = dir * (shiftClick ? 25 : 5);

        switch (slot) {
            case 10, 11, 12, 15, 21 -> context.read(player, uuid, snap -> {
                if (snap == null) return;
                if (!MtvPeripheralController.canEdit(player, snap)) {
                    player.sendMessage("该 MTV 播放器为私有，只有创建者或拥有 mtv.player.edit.others 权限的玩家可以编辑。");
                    return;
                }
                switch (slot) {
                    case 10 -> context.updateAndRefresh(player, uuid,
                            done -> context.manager().moveEntity(uuid, posStep, 0, 0, done));
                    case 11 -> context.updateAndRefresh(player, uuid,
                            done -> context.manager().moveEntity(uuid, 0, posStep, 0, done));
                    case 12 -> context.updateAndRefresh(player, uuid,
                            done -> context.manager().moveEntity(uuid, 0, 0, posStep, done));
                    case 15 -> context.updateAndRefresh(player, uuid,
                            done -> context.manager().snapEntityPosition(uuid, done));
                    case 21 -> context.updateAndRefresh(player, uuid,
                            done -> context.manager().setEntityRotation(uuid, 0, 0, done));
                }
            });
            case 19 -> context.read(player, uuid, snap -> {
                if (snap == null) return;
                if (!MtvPeripheralController.canEdit(player, snap)) {
                    player.sendMessage("该 MTV 播放器为私有，只有创建者或拥有 mtv.player.edit.others 权限的玩家可以编辑。");
                    return;
                }
                context.updateAndRefresh(player, uuid,
                        done -> context.manager().setEntityRotation(uuid,
                                snap.getYaw() + rotStep, snap.getPitch(), done));
            });
            case 20 -> context.read(player, uuid, snap -> {
                if (snap == null) return;
                if (!MtvPeripheralController.canEdit(player, snap)) {
                    player.sendMessage("该 MTV 播放器为私有，只有创建者或拥有 mtv.player.edit.others 权限的玩家可以编辑。");
                    return;
                }
                context.updateAndRefresh(player, uuid,
                        done -> context.manager().setEntityRotation(uuid,
                                snap.getYaw(), snap.getPitch() + rotStep, done));
            });
            case 16 -> {
                if (!MtvPeripheralController.checkPerm(player, "mtv.player.edit")) return true;
                context.read(player, uuid, snap -> {
                    if (snap == null) return;
                    if (!MtvPeripheralController.canEdit(player, snap)) {
                        player.sendMessage("该 MTV 播放器为私有，只有创建者或拥有 mtv.player.edit.others 权限的玩家可以编辑。");
                        return;
                    }
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().teleportToPlayer(uuid, player, done));
                });
            }
            default -> { return false; }
        }
        return true;
    }
}
