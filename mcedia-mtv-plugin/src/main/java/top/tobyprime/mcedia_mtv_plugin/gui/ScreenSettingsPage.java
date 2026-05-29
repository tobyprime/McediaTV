package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil;

public class ScreenSettingsPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.SCREEN_SETTINGS; }

    @Override
    public Component getTitle(PageEntry entry) {
        String periph = entry.getPeripheralId() != null ? entry.getPeripheralId() : "屏幕";
        return Component.text("屏幕 " + periph);
    }

    @Override
    public Material icon() { return Material.MAP; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        String periphId = entry.getPeripheralId();
        if (uuid == null || periphId == null) { player.closeInventory(); return; }
        context.read(player, uuid, snap -> {
            if (snap == null) return;
            var sc = snap.findScreen(periphId);
            if (sc == null) { player.closeInventory(); return; }

            var inv = createInventory(entry);
            inv.setItem(10, item(Material.MAP, "宽: " + String.format("%.1f", sc.getWidth()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(11, item(Material.MAP, "高: " + String.format("%.1f", sc.getHeight()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(12, item(Material.STRUCTURE_VOID, "重置尺寸", "点击恢复默认"));
            inv.setItem(19, item(Material.SEA_LANTERN, "亮度: " + sc.getMinBrightness(),
                    "左键 -1 / 右键 +1"));
            inv.setItem(20, item(Material.PAINTING, "填充: " + sc.getFillMode(), "点击切换"));
            inv.setItem(21, item(Material.STRUCTURE_VOID, "重置基础", "亮度/填充/纹理"));
            inv.setItem(22, item(sc.isDanmakuVisible() ? Material.LIME_DYE : Material.GRAY_DYE,
                    "弹幕: " + (sc.isDanmakuVisible() ? "开" : "关"), "点击切换"));
            inv.setItem(28, item(Material.RED_WOOL, "X: " + String.format("%.1f", sc.getOffsetX()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(29, item(Material.GREEN_WOOL, "Y: " + String.format("%.1f", sc.getOffsetY()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(30, item(Material.BLUE_WOOL, "Z: " + String.format("%.1f", sc.getOffsetZ()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(31, item(Material.STRUCTURE_VOID, "重置偏移", "点击恢复默认"));
            inv.setItem(32, item(Material.LIGHTNING_ROD, "吸附偏移", "四舍五入到整数"));
            inv.setItem(33, item(Material.ENDER_EYE, "设到玩家位置", "偏移设为你脚下"));

            float[] euler = EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
            inv.setItem(37, item(Material.COMPASS, "Roll(Z): " + String.format("%.0f", euler[0]) + "°",
                    "左键 -1 / 右键 +1"));
            inv.setItem(38, item(Material.COMPASS, "Pitch(Y): " + String.format("%.0f", euler[1]) + "°",
                    "左键 -1 / 右键 +1"));
            inv.setItem(39, item(Material.COMPASS, "Yaw(X): " + String.format("%.0f", euler[2]) + "°",
                    "左键 -1 / 右键 +1"));
            inv.setItem(40, item(Material.STRUCTURE_VOID, "重置旋转", "点击恢复默认"));
            inv.setItem(49, item(Material.ARROW, "返回外设列表"));
            inv.setItem(53, item(Material.TNT, "删除外设"));
            setupTitleBar(inv, nav, entry);
            openInventory(player, inv);
        });
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID uuid = entry.getEntityUuid();
        String periphId = entry.getPeripheralId();
        if (uuid == null || periphId == null) return false;

        context.read(player, uuid, snap -> {
            if (snap == null) return;
            var sc = snap.findScreen(periphId);
            if (sc == null) return;

            float dir = rightClick ? 1 : -1;
            float fine = dir * 0.1F;
            float sizeStep = shiftClick ? dir * 1.0F : fine;
            float offsetStep = shiftClick ? dir * 1.0F : fine;
            int brightnessStep = (rightClick ? 1 : -1) * (shiftClick ? 5 : 1);
            float rotStep = dir * (shiftClick ? 25 : 5);

            switch (slot) {
                case 10 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().updateScreenSize(uuid, periphId, sizeStep, 0, done));
                case 11 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().updateScreenSize(uuid, periphId, 0, sizeStep, done));
                case 12 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetScreenSize(uuid, periphId, done));
                case 19 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenBrightness(uuid, periphId,
                                sc.getMinBrightness() + brightnessStep, done));
                case 20 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().toggleScreenFill(uuid, periphId, done));
                case 21 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetScreenBasic(uuid, periphId, done));
                case 22 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenDanmakuVisible(uuid, periphId,
                                !sc.isDanmakuVisible(), done));
                case 28 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffset(uuid, periphId,
                                sc.getOffsetX() + offsetStep, sc.getOffsetY(), sc.getOffsetZ(), done));
                case 29 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffset(uuid, periphId,
                                sc.getOffsetX(), sc.getOffsetY() + offsetStep, sc.getOffsetZ(), done));
                case 30 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffset(uuid, periphId,
                                sc.getOffsetX(), sc.getOffsetY(), sc.getOffsetZ() + offsetStep, done));
                case 31 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetScreenOffset(uuid, periphId, done));
                case 32 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().snapScreenOffset(uuid, periphId, done));
                case 33 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffsetToPlayer(uuid, periphId, player, done));
                case 37 -> {
                    float[] euler = EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[0] += rotStep;
                    float[] q = EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done));
                }
                case 38 -> {
                    float[] euler = EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[1] += rotStep;
                    float[] q = EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done));
                }
                case 39 -> {
                    float[] euler = EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[2] += rotStep;
                    float[] q = EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done));
                }
                case 40 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetScreenRotation(uuid, periphId, done));
                case 49 -> context.navigateTo(player, MtvGui.GuiType.PERIPHERAL_LIST, uuid);
                case 53 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.delete")) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().removePeripheral(uuid, "screen", periphId, done));
                }
            }
        });
        return true;
    }
}
