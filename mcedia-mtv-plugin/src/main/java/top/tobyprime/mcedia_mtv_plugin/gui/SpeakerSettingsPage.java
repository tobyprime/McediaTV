package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;

public class SpeakerSettingsPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.SPEAKER_SETTINGS; }

    @Override
    public Component getTitle(PageEntry entry) {
        String periph = entry.getPeripheralId() != null ? entry.getPeripheralId() : "扬声器";
        return Component.text("扬声器 " + periph);
    }

    @Override
    public Material icon() { return Material.NOTE_BLOCK; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        String periphId = entry.getPeripheralId();
        if (uuid == null || periphId == null) { player.closeInventory(); return; }
        context.read(player, uuid, snap -> {
            if (snap == null) return;
            var sp = snap.findSpeaker(periphId);
            if (sp == null) { player.closeInventory(); return; }

            var inv = createInventory(entry);

            String volStr = String.format("%.1f", sp.getVolume());
            String rngStr = String.format("%.0f", sp.getMaxRange());

            // ── 左栏 (9,18,27 / 11,20,29): 竖直两列 ──
            //     ↑ 绿混凝土 (增量)
            //     ■ 语义 Item (当前值)
            //     ↓ 红混凝土 (减量)

            // 音量列: 9↑ 18■ 27↓
            inv.setItem(9,  item(Material.GREEN_CONCRETE,
                    "§a+ §7增大音量", "§7当前: §f" + volStr, "§7点击 +0.1"));
            inv.setItem(18, item(Material.NOTE_BLOCK,
                    "§6🔊 音量: " + volStr));
            inv.setItem(27, item(Material.RED_CONCRETE,
                    "§c− §7减小音量", "§7当前: §f" + volStr, "§7点击 −0.1"));

            // 范围列: 11↑ 20■ 29↓
            inv.setItem(11, item(Material.GREEN_CONCRETE,
                    "§a+ §7扩大范围", "§7当前: §f" + rngStr, "§7点击 +1"));
            inv.setItem(20, item(Material.BELL,
                    "§b🔔 范围: " + rngStr));
            inv.setItem(29, item(Material.RED_CONCRETE,
                    "§c− §7缩小范围", "§7当前: §f" + rngStr, "§7点击 −1"));

            // ── 右栏 ──
            inv.setItem(17, item(Material.JUKEBOX,
                    "§d🎵 声道: " + sp.getChannelMode(),
                    "§7点击切换 mix / left / right"));

            inv.setItem(35, item(Material.STRUCTURE_VOID,
                    "§e↺ 重置音频", "§7音量/范围/声道恢复默认"));

            // ── Row 5 (45-53): XYZ + 吸附 + 设到玩家 + 删除 ──

            inv.setItem(45, item(Material.RED_WOOL,
                    "§c🔴 X 偏移: " + String.format("%.1f", sp.getOffsetX()),
                    "§7左键 §c-0.1  §7| 右键 §a+0.1", "§7潜行 ×1.0"));

            inv.setItem(46, item(Material.GREEN_WOOL,
                    "§a🟢 Y 偏移: " + String.format("%.1f", sp.getOffsetY()),
                    "§7左键 §c-0.1  §7| 右键 §a+0.1", "§7潜行 ×1.0"));

            inv.setItem(47, item(Material.BLUE_WOOL,
                    "§9🔵 Z 偏移: " + String.format("%.1f", sp.getOffsetZ()),
                    "§7左键 §c-0.1  §7| 右键 §a+0.1", "§7潜行 ×1.0"));

            inv.setItem(48, item(Material.STRUCTURE_VOID,
                    "§e↺ 重置偏移", "§7偏移值恢复为零"));

            inv.setItem(49, item(Material.LIGHTNING_ROD,
                    "§6⚡ 吸附偏移", "§7偏移四舍五入到整数"));

            inv.setItem(50, item(Material.ENDER_EYE,
                    "§d📌 设到玩家位置", "§7将偏移设为你脚下位置"));

            inv.setItem(53, item(Material.TNT,
                    "§c☠ 删除外设",
                    "§4⚠ 该操作不可撤销！", "§7将永久移除此外设"));

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
            var sp = snap.findSpeaker(periphId);
            if (sp == null) return;

            // Left/right direction for single-item controls
            float dir = rightClick ? 1 : -1;
            float fine = dir * 0.1F;
            float offsetStep = shiftClick ? dir * 1.0F : fine;

            switch (slot) {
                // ── 左栏 音量列: 9⬆ / 27⬇ ──
                case 9 -> {
                    float step = shiftClick ? 1.0F : 0.1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setSpeakerVolume(uuid, periphId,
                                    sp.getVolume() + step, done));
                }
                case 27 -> {
                    float step = shiftClick ? -1.0F : -0.1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setSpeakerVolume(uuid, periphId,
                                    sp.getVolume() + step, done));
                }
                // ── 左栏 范围列: 11⬆ / 29⬇ ──
                case 11 -> {
                    float step = shiftClick ? 5F : 1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setSpeakerRange(uuid, periphId,
                                    sp.getMaxRange() + step, done));
                }
                case 29 -> {
                    float step = shiftClick ? -5F : -1F;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setSpeakerRange(uuid, periphId,
                                    sp.getMaxRange() + step, done));
                }
                // ── 右栏 ──
                case 17 -> {
                    String next = switch (sp.getChannelMode()) {
                        case "left" -> "right";
                        case "right" -> "mix";
                        default -> "left";
                    };
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setSpeakerChannelMode(uuid, periphId, next, done));
                }
                case 35 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetSpeakerAudio(uuid, periphId, done));

                // ── Row 5: XYZ + 吸附 + 传送 + 删除 ──
                case 45 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffset(uuid, periphId,
                                sp.getOffsetX() + offsetStep, sp.getOffsetY(), sp.getOffsetZ(), done));
                case 46 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffset(uuid, periphId,
                                sp.getOffsetX(), sp.getOffsetY() + offsetStep, sp.getOffsetZ(), done));
                case 47 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffset(uuid, periphId,
                                sp.getOffsetX(), sp.getOffsetY(), sp.getOffsetZ() + offsetStep, done));
                case 48 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetSpeakerOffset(uuid, periphId, done));
                case 49 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().snapSpeakerOffset(uuid, periphId, done));
                case 50 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffsetToPlayer(uuid, periphId, player, done));
                case 53 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.delete")) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().removePeripheral(uuid, "speaker", periphId, done));
                }
            }
        });
        return true;
    }
}
