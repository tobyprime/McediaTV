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
            inv.setItem(10, item(Material.NOTE_BLOCK, "音量: " + String.format("%.1f", sp.getVolume()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(11, item(Material.BELL, "范围: " + String.format("%.0f", sp.getMaxRange()),
                    "左键 -1 / 右键 +1"));
            inv.setItem(12, item(Material.JUKEBOX, "声道: " + sp.getChannelMode(),
                    "点击切换 mix/left/right"));
            inv.setItem(13, item(Material.STRUCTURE_VOID, "重置音域", "点击恢复默认"));
            inv.setItem(28, item(Material.RED_WOOL, "X: " + String.format("%.1f", sp.getOffsetX()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(29, item(Material.GREEN_WOOL, "Y: " + String.format("%.1f", sp.getOffsetY()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(30, item(Material.BLUE_WOOL, "Z: " + String.format("%.1f", sp.getOffsetZ()),
                    "左键 -0.1 / 右键 +0.1"));
            inv.setItem(31, item(Material.STRUCTURE_VOID, "重置偏移", "点击恢复默认"));
            inv.setItem(32, item(Material.LIGHTNING_ROD, "吸附偏移", "四舍五入到整数"));
            inv.setItem(33, item(Material.ENDER_EYE, "设到玩家位置", "偏移设为你脚下"));
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
            var sp = snap.findSpeaker(periphId);
            if (sp == null) return;

            float dir = rightClick ? 1 : -1;
            float fine = dir * 0.1F;
            float volumeStep = shiftClick ? dir * 1.0F : fine;
            float rangeStep = dir * (shiftClick ? 5 : 1);
            float offsetStep = shiftClick ? dir * 1.0F : fine;

            switch (slot) {
                case 10 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerVolume(uuid, periphId,
                                sp.getVolume() + volumeStep, done));
                case 11 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerRange(uuid, periphId,
                                sp.getMaxRange() + rangeStep, done));
                case 12 -> {
                    String next = switch (sp.getChannelMode()) {
                        case "left" -> "right";
                        case "right" -> "mix";
                        default -> "left";
                    };
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setSpeakerChannelMode(uuid, periphId, next, done));
                }
                case 13 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetSpeakerAudio(uuid, periphId, done));
                case 28 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffset(uuid, periphId,
                                sp.getOffsetX() + offsetStep, sp.getOffsetY(), sp.getOffsetZ(), done));
                case 29 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffset(uuid, periphId,
                                sp.getOffsetX(), sp.getOffsetY() + offsetStep, sp.getOffsetZ(), done));
                case 30 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffset(uuid, periphId,
                                sp.getOffsetX(), sp.getOffsetY(), sp.getOffsetZ() + offsetStep, done));
                case 31 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().resetSpeakerOffset(uuid, periphId, done));
                case 32 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().snapSpeakerOffset(uuid, periphId, done));
                case 33 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setSpeakerOffsetToPlayer(uuid, periphId, player, done));
                case 49 -> context.navigateTo(player, MtvGui.GuiType.PERIPHERAL_LIST, uuid);
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
