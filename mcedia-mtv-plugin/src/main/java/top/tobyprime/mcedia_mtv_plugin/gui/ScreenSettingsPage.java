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

            String wStr = String.format("%.1f", sc.getWidth());
            String hStr = String.format("%.1f", sc.getHeight());
            int bright = sc.getMinBrightness();

            // ── 左栏: 竖直三列，贴着左边缘 ──
            //     ↑ 绿混凝土 (增量)   ■ 语义 Item   ↓ 红混凝土 (减量)

            // 高度列: 9↑ 18■ 27↓
            inv.setItem(9,  item(Material.GREEN_CONCRETE,
                    "§a+ §7增高", "§7当前: §f" + hStr, "§7点击 +0.1"));
            inv.setItem(18, item(Material.MAP,
                    "§6📐 高度: " + hStr));
            inv.setItem(27, item(Material.RED_CONCRETE,
                    "§c− §7降低", "§7当前: §f" + hStr, "§7点击 −0.1"));

            // 宽度列: 11↑ 20■ 29↓
            inv.setItem(11, item(Material.GREEN_CONCRETE,
                    "§a+ §7增宽", "§7当前: §f" + wStr, "§7点击 +0.1"));
            inv.setItem(20, item(Material.MAP,
                    "§6📐 宽度: " + wStr));
            inv.setItem(29, item(Material.RED_CONCRETE,
                    "§c− §7收窄", "§7当前: §f" + wStr, "§7点击 −0.1"));

            // 亮度列: 13↑ 22■ 31↓
            inv.setItem(13, item(Material.GREEN_CONCRETE,
                    "§a+ §7增亮", "§7当前: §f" + bright, "§7点击 +1"));
            inv.setItem(22, item(Material.SEA_LANTERN,
                    "§b💡 亮度: " + bright));
            inv.setItem(31, item(Material.RED_CONCRETE,
                    "§c− §7调暗", "§7当前: §f" + bright, "§7点击 −1"));

            // ── 右栏 (17,26,35): 开关 / 操作 ──

            inv.setItem(17, item(Material.PAINTING,
                    "§d🎨 填充: " + sc.getFillMode(), "§7点击切换填充模式"));

            var danmakuIcon = sc.isDanmakuVisible() ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(26, item(danmakuIcon,
                    "§a💬 弹幕: " + (sc.isDanmakuVisible() ? "开" : "关"), "§7点击切换弹幕显示"));

            inv.setItem(35, item(Material.STRUCTURE_VOID,
                    "§e↺ 重置显示", "§7亮度/填充/纹理恢复默认"));

            // ── Row 4 (36-44): XYZ + 重置 + 吸附 ──

            inv.setItem(36, item(Material.RED_WOOL,
                    "§c🔴 X 偏移: " + String.format("%.1f", sc.getOffsetX()),
                    "§7左键 §c-0.1  §7| 右键 §a+0.1", "§7潜行 ×1.0"));

            inv.setItem(37, item(Material.GREEN_WOOL,
                    "§a🟢 Y 偏移: " + String.format("%.1f", sc.getOffsetY()),
                    "§7左键 §c-0.1  §7| 右键 §a+0.1", "§7潜行 ×1.0"));

            inv.setItem(38, item(Material.BLUE_WOOL,
                    "§9🔵 Z 偏移: " + String.format("%.1f", sc.getOffsetZ()),
                    "§7左键 §c-0.1  §7| 右键 §a+0.1", "§7潜行 ×1.0"));

            inv.setItem(39, item(Material.STRUCTURE_VOID,
                    "§e↺ 重置偏移", "§7偏移值恢复为零"));

            inv.setItem(40, item(Material.LIGHTNING_ROD,
                    "§6⚡ 吸附偏移", "§7偏移四舍五入到整数"));

            // ── Row 5 (45-53): 旋转 + 重置 + 传送 + 删除 ──

            float[] euler = EulerAngleUtil.toEuler(
                    sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());

            inv.setItem(45, item(Material.COMPASS,
                    "§6🔄 Roll(Z): " + String.format("%.0f", euler[0]) + "°",
                    "§7左键 §c-5°  §7| 右键 §a+5°", "§7潜行 ×25°"));

            inv.setItem(46, item(Material.COMPASS,
                    "§6🔄 Pitch(Y): " + String.format("%.0f", euler[1]) + "°",
                    "§7左键 §c-5°  §7| 右键 §a+5°", "§7潜行 ×25°"));

            inv.setItem(47, item(Material.COMPASS,
                    "§6🔄 Yaw(X): " + String.format("%.0f", euler[2]) + "°",
                    "§7左键 §c-5°  §7| 右键 §a+5°", "§7潜行 ×25°"));

            inv.setItem(48, item(Material.STRUCTURE_VOID,
                    "§e↺ 重置旋转", "§7旋转角度恢复为零"));

            inv.setItem(49, item(Material.ENDER_EYE,
                    "§d📌 设到玩家位置", "§7将偏移设为你脚下位置"));

            inv.setItem(53, item(Material.TNT,
                    "§c☠ 删除外设",
                    "§4⚠ 该操作不可撤销！", "§7将永久移除此屏幕"));

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

            // Left/right direction for single-item controls
            float dir = rightClick ? 1 : -1;
            float fine = dir * 0.1F;
            float offsetStep = shiftClick ? dir * 1.0F : fine;
            int brightnessStep = (rightClick ? 1 : -1) * (shiftClick ? 5 : 1);
            float rotStep = dir * (shiftClick ? 25 : 5);

            switch (slot) {
                // ── 左栏 高度列: 9⬆ / 27⬇ ──
                case 9 -> {
                    float step = shiftClick ? 1.0F : 0.1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().updateScreenSize(uuid, periphId, 0, step, done));
                }
                case 27 -> {
                    float step = shiftClick ? -1.0F : -0.1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().updateScreenSize(uuid, periphId, 0, step, done));
                }
                // ── 左栏 宽度列: 11⬆ / 29⬇ ──
                case 11 -> {
                    float step = shiftClick ? 1.0F : 0.1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().updateScreenSize(uuid, periphId, step, 0, done));
                }
                case 29 -> {
                    float step = shiftClick ? -1.0F : -0.1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().updateScreenSize(uuid, periphId, step, 0, done));
                }
                // ── 左栏 亮度列: 13⬆ / 31⬇ ──
                case 13 -> {
                    int step = shiftClick ? 5 : 1;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenBrightness(uuid, periphId,
                                    sc.getMinBrightness() + step, done));
                }
                case 31 -> {
                    int step = shiftClick ? -5 : -1;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenBrightness(uuid, periphId,
                                    sc.getMinBrightness() + step, done));
                }

                // ── 右栏 ──
                case 17 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().toggleScreenFill(uuid, periphId, done));
                case 26 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenDanmakuVisible(uuid, periphId,
                                !sc.isDanmakuVisible(), done));
                case 35 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetScreenBasic(uuid, periphId, done));

                // ── Row 4: XYZ + 重置 + 吸附 ──
                case 36 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffset(uuid, periphId,
                                sc.getOffsetX() + offsetStep, sc.getOffsetY(), sc.getOffsetZ(), done));
                case 37 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffset(uuid, periphId,
                                sc.getOffsetX(), sc.getOffsetY() + offsetStep, sc.getOffsetZ(), done));
                case 38 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffset(uuid, periphId,
                                sc.getOffsetX(), sc.getOffsetY(), sc.getOffsetZ() + offsetStep, done));
                case 39 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetScreenOffset(uuid, periphId, done));
                case 40 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().snapScreenOffset(uuid, periphId, done));

                // ── Row 5: 旋转 + 重置 + 传送 + 删除 ──
                case 45 -> {
                    float[] euler = EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[0] += rotStep;
                    float[] q = EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done));
                }
                case 46 -> {
                    float[] euler = EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[1] += rotStep;
                    float[] q = EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done));
                }
                case 47 -> {
                    float[] euler = EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[2] += rotStep;
                    float[] q = EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done));
                }
                case 48 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetScreenRotation(uuid, periphId, done));
                case 49 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setScreenOffsetToPlayer(uuid, periphId, player, done));
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
