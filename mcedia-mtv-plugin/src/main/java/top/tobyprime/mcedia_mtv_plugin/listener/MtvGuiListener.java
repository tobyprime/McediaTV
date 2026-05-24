package top.tobyprime.mcedia_mtv_plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui.GuiType;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.UUID;
import java.util.function.Consumer;

public class MtvGuiListener implements Listener {
    private static final int[] PERIPH_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final MtvGui gui;
    private final MtvPlaybackController playbackController;

    public MtvGuiListener(MtvGui gui, MtvPlaybackController playbackController) {
        this.gui = gui;
        this.playbackController = playbackController;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MtvGui.MtvHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0) return;
        if (event.getCurrentItem() == null) return;
        click(player, holder, event.getRawSlot(), event.isRightClick(), event.isShiftClick());
    }

    private void click(Player player, MtvGui.MtvHolder holder, int slot, boolean rightClick, boolean shiftClick) {
        switch (holder.getType()) {
            case MAIN_MENU -> {
                if (slot == 11) { player.closeInventory(); player.performCommand("mtv create"); }
                else if (slot == 15) player.performCommand("mtv edit");
            }
            case PLAYER_MENU -> handlePlayerMenu(player, holder, slot, rightClick, shiftClick);
            case PERIPHERAL_LIST -> handlePeripheralList(player, holder, slot);
            case ADD_PERIPHERAL -> handleAddPeripheral(player, holder, slot);
            case WORLD_TRANSFORM -> handleWorldTransform(player, holder, slot, rightClick, shiftClick);
            case SCREEN_SETTINGS -> handleScreenSettings(player, holder, slot, rightClick, shiftClick);
            case SPEAKER_SETTINGS -> handleSpeakerSettings(player, holder, slot, rightClick, shiftClick);
            case CHANNEL_MENU -> handleChannelMenu(player, holder, slot, rightClick, shiftClick);
        }
    }

    private void handlePlayerMenu(Player player, MtvGui.MtvHolder holder, int slot, boolean rightClick, boolean shiftClick) {
        var uuid = holder.getEntityUuid();
        if (uuid == null) return;
        read(player, uuid, snap -> {
            if (snap == null) return;
            String name = snap.getName();

            switch (slot) {
                case 4 -> {
                    player.closeInventory();
                    player.sendMessage("请输入新名称。");
                    gui.setAwaitingInput(player, MtvGui.GuiType.PLAYER_MENU, uuid, "rename");
                }
                case 11 -> {
                    float delta = (rightClick ? 1 : -1) * (shiftClick ? 1.0F : 0.25F);
                    float v = Math.max(0.25F, Math.min(4.0F, snap.getSpeed() + delta));
                    player.performCommand("mtv speed " + v + " " + name);
                    gui.reopenPage(player, GuiType.PLAYER_MENU, uuid);
                }
                case 12 -> gui.openPeripheralList(player, snap);
                case 13 -> gui.openWorldTransform(player, snap);
                case 15 -> {
                    if (MtvPeripheralController.checkPerm(player, "mcedia.mtv.teleport")) {
                        player.performCommand("mtv tp " + snap.getName());
                    }
                }
                case 41 -> gui.openChannelMenu(player, snap);
                case 40 -> {
                    player.closeInventory();
                    player.sendMessage("请输入播放 URL（URL 或 BV 号）。");
                    gui.setAwaitingInput(player, MtvGui.GuiType.PLAYER_MENU, uuid, "media_url");
                }
                case 47 -> updateAndReopen(player, uuid, done -> playbackController.updateStartAt(uuid, 0L, done), GuiType.PLAYER_MENU, null);
                case 48 -> {
                    long delta = shiftClick ? -10_000_000L : -1_000_000L;
                    updateAndReopen(player, uuid, done -> playbackController.seekRelative(uuid, delta, done), GuiType.PLAYER_MENU, null);
                }
                case 49 -> updateAndReopen(player, uuid, done -> playbackController.togglePause(uuid, done), GuiType.PLAYER_MENU, null);
                case 50 -> {
                    long delta = shiftClick ? 10_000_000L : 1_000_000L;
                    updateAndReopen(player, uuid, done -> playbackController.seekRelative(uuid, delta, done), GuiType.PLAYER_MENU, null);
                }
                case 51 -> {
                    player.closeInventory();
                    player.sendMessage("请输入跳转位置的微秒值。");
                    gui.setAwaitingInput(player, MtvGui.GuiType.PLAYER_MENU, uuid, "start_at");
                }
                case 53 -> {
                    if (MtvPeripheralController.checkPerm(player, "mcedia.mtv.delete")) {
                        player.closeInventory();
                        player.performCommand("mtv remove " + snap.getName());
                    }
                }
            }
        });
    }

    private void handlePeripheralList(Player player, MtvGui.MtvHolder holder, int slot) {
        var uuid = holder.getEntityUuid();
        if (uuid == null) return;
        read(player, uuid, snap -> {
            if (snap == null) return;

            if (slot == 43) {
                gui.openAddPeripheral(player, snap);
            } else if (slot == 49) {
                gui.openPlayerMenu(player, snap);
            } else {
                int idx = indexOf(PERIPH_SLOTS, slot);
                if (idx < 0) return;
                int screenCount = snap.getScreens().size();
                if (idx < screenCount) {
                    var s = snap.getScreens().get(idx);
                    gui.openScreenSettings(player, snap, s.getId());
                } else {
                    int speakerIdx = idx - screenCount;
                    if (speakerIdx < snap.getSpeakers().size()) {
                        var s = snap.getSpeakers().get(speakerIdx);
                        gui.openSpeakerSettings(player, snap, s.getId());
                    }
                }
            }
        });
    }

    private void handleWorldTransform(Player player, MtvGui.MtvHolder holder, int slot, boolean rightClick, boolean shiftClick) {
        var uuid = holder.getEntityUuid();
        if (uuid == null) return;
        float dir = rightClick ? 1 : -1;
        float posStep = dir * (shiftClick ? 1.0F : 0.1F);
        float rotStep = dir * (shiftClick ? 25 : 5);

        switch (slot) {
            case 10 -> updateAndReopen(player, uuid, done -> gui.getManager().moveEntity(uuid, posStep, 0, 0, done), GuiType.WORLD_TRANSFORM, null);
            case 11 -> updateAndReopen(player, uuid, done -> gui.getManager().moveEntity(uuid, 0, posStep, 0, done), GuiType.WORLD_TRANSFORM, null);
            case 12 -> updateAndReopen(player, uuid, done -> gui.getManager().moveEntity(uuid, 0, 0, posStep, done), GuiType.WORLD_TRANSFORM, null);
            case 19 -> read(player, uuid, snap -> {
                if (snap != null) updateAndReopen(player, uuid, done -> gui.getManager().setEntityRotation(uuid, snap.getYaw() + rotStep, snap.getPitch(), done), GuiType.WORLD_TRANSFORM, null);
            });
            case 20 -> read(player, uuid, snap -> {
                if (snap != null) updateAndReopen(player, uuid, done -> gui.getManager().setEntityRotation(uuid, snap.getYaw(), snap.getPitch() + rotStep, done), GuiType.WORLD_TRANSFORM, null);
            });
            case 21 -> updateAndReopen(player, uuid, done -> gui.getManager().setEntityRotation(uuid, 0, 0, done), GuiType.WORLD_TRANSFORM, null);
            case 15 -> updateAndReopen(player, uuid, done -> gui.getManager().snapEntityPosition(uuid, done), GuiType.WORLD_TRANSFORM, null);
            case 16 -> {
                if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.movehere")) return;
                updateAndReopen(player, uuid, done -> gui.getManager().teleportToPlayer(uuid, player, done), GuiType.WORLD_TRANSFORM, null);
            }
            case 49 -> read(player, uuid, snap -> {
                if (snap != null) gui.openPlayerMenu(player, snap);
            });
        }
    }

    private void handleChannelMenu(Player player, MtvGui.MtvHolder holder, int slot, boolean rightClick, boolean shiftClick) {
        var uuid = holder.getEntityUuid();
        if (uuid == null) return;

        switch (slot) {
            case 10 -> read(player, uuid, snap -> {
                if (snap != null) {
                    float delta = (rightClick ? 1 : -1) * (shiftClick ? 1.0F : 0.25F);
                    float speed = Math.max(0.25F, Math.min(4.0F, snap.getSpeed() + delta));
                    updateAndReopen(player, uuid, done -> playbackController.updateSpeed(uuid, speed, done), GuiType.CHANNEL_MENU, null);
                }
            });
            case 11 -> {
                player.closeInventory();
                player.sendMessage("请输入播放 URL（URL 或 BV 号）。");
                gui.setAwaitingInput(player, GuiType.CHANNEL_MENU, uuid, "channel_media_url");
            }
            case 12 -> updateAndReopen(player, uuid, done -> playbackController.cyclePlayOrderMode(uuid, done), GuiType.CHANNEL_MENU, null);
            case 13 -> updateAndReopen(player, uuid, done -> playbackController.playPreviousManual(uuid, done), GuiType.CHANNEL_MENU, null);
            case 14 -> updateAndReopen(player, uuid, done -> playbackController.togglePause(uuid, done), GuiType.CHANNEL_MENU, null);
            case 15 -> updateAndReopen(player, uuid, done -> playbackController.playNextManual(uuid, done), GuiType.CHANNEL_MENU, null);
            case 16 -> {
                player.closeInventory();
                player.sendMessage("请输入跳转位置的微秒值。");
                gui.setAwaitingInput(player, GuiType.CHANNEL_MENU, uuid, "start_at");
            }
            case 19 -> {
                player.closeInventory();
                player.sendMessage("请输入要首加的 URL 或 BV 号。");
                gui.setAwaitingInput(player, GuiType.CHANNEL_MENU, uuid, "channel_prepend");
            }
            case 20 -> {
                player.closeInventory();
                player.sendMessage("请输入要尾加的 URL 或 BV 号。");
                gui.setAwaitingInput(player, GuiType.CHANNEL_MENU, uuid, "channel_append");
            }
            case 21 -> updateAndReopen(player, uuid, done -> playbackController.updateStartAt(uuid, 0L, done), GuiType.CHANNEL_MENU, null);
            case 22 -> {
                long delta = shiftClick ? -10_000_000L : -1_000_000L;
                updateAndReopen(player, uuid, done -> playbackController.seekRelative(uuid, delta, done), GuiType.CHANNEL_MENU, null);
            }
            case 23 -> {
                long delta = shiftClick ? 10_000_000L : 1_000_000L;
                updateAndReopen(player, uuid, done -> playbackController.seekRelative(uuid, delta, done), GuiType.CHANNEL_MENU, null);
            }
            case 47 -> read(player, uuid, snap -> {
                if (snap != null) gui.openPlayerMenu(player, snap);
            });
            case 49 -> gui.reopenPage(player, GuiType.CHANNEL_MENU, uuid);
            default -> {
                int index = indexOf(MtvGui.CHANNEL_PLAYLIST_SLOTS, slot);
                if (index >= 0) {
                    if (shiftClick && rightClick) {
                        updateAndReopen(player, uuid, done -> playbackController.movePlaylistItemToBack(uuid, index, done), GuiType.CHANNEL_MENU, null);
                    } else if (shiftClick) {
                        updateAndReopen(player, uuid, done -> playbackController.movePlaylistItemToFront(uuid, index, done), GuiType.CHANNEL_MENU, null);
                    } else if (rightClick) {
                        updateAndReopen(player, uuid, done -> playbackController.removePlaylistItem(uuid, index, done), GuiType.CHANNEL_MENU, null);
                    } else {
                        updateAndReopen(player, uuid, done -> playbackController.playPlaylistIndex(uuid, index, done), GuiType.CHANNEL_MENU, null);
                    }
                }
            }
        }
    }

    private void handleAddPeripheral(Player player, MtvGui.MtvHolder holder, int slot) {
        var uuid = holder.getEntityUuid();
        if (uuid == null) return;

        if (slot == 11) {
            gui.getManager().addScreen(uuid, screen -> gui.reopenPage(player, GuiType.PERIPHERAL_LIST, uuid));
        } else if (slot == 15) {
            gui.getManager().addSpeaker(uuid, speaker -> gui.reopenPage(player, GuiType.PERIPHERAL_LIST, uuid));
        }
    }

    private void handleScreenSettings(Player player, MtvGui.MtvHolder holder, int slot, boolean rightClick, boolean shiftClick) {
        var uuid = holder.getEntityUuid();
        var periphId = holder.getPeripheralId();
        if (uuid == null || periphId == null) return;

        read(player, uuid, snap -> {
            if (snap == null) return;
            var sc = snap.findScreen(periphId);
            if (sc == null) return;

            float dir = rightClick ? 1 : -1;
            float fine = dir * 0.1F;
            float coarse = dir * 1.0F;
            float sizeStep = shiftClick ? coarse : fine;
            float offsetStep = shiftClick ? coarse : fine;
            int brightnessStep = (rightClick ? 1 : -1) * (shiftClick ? 5 : 1);
            float rotStep = dir * (shiftClick ? 25 : 5);

            switch (slot) {
                case 10 -> updateAndReopen(player, uuid, done -> gui.getManager().updateScreenSize(uuid, periphId, sizeStep, 0, done), GuiType.SCREEN_SETTINGS, periphId);
                case 11 -> updateAndReopen(player, uuid, done -> gui.getManager().updateScreenSize(uuid, periphId, 0, sizeStep, done), GuiType.SCREEN_SETTINGS, periphId);
                case 12 -> updateAndReopen(player, uuid, done -> gui.getManager().resetScreenSize(uuid, periphId, done), GuiType.SCREEN_SETTINGS, periphId);
                case 19 -> updateAndReopen(player, uuid, done -> gui.getManager().setScreenBrightness(uuid, periphId, sc.getMinBrightness() + brightnessStep, done), GuiType.SCREEN_SETTINGS, periphId);
                case 20 -> updateAndReopen(player, uuid, done -> gui.getManager().toggleScreenFill(uuid, periphId, done), GuiType.SCREEN_SETTINGS, periphId);
                case 21 -> updateAndReopen(player, uuid, done -> gui.getManager().resetScreenBasic(uuid, periphId, done), GuiType.SCREEN_SETTINGS, periphId);
                case 22 -> updateAndReopen(player, uuid, done -> gui.getManager().setScreenDanmakuVisible(uuid, periphId, !sc.isDanmakuVisible(), done), GuiType.SCREEN_SETTINGS, periphId);
                case 28 -> updateAndReopen(player, uuid, done -> gui.getManager().setScreenOffset(uuid, periphId, sc.getOffsetX() + offsetStep, sc.getOffsetY(), sc.getOffsetZ(), done), GuiType.SCREEN_SETTINGS, periphId);
                case 29 -> updateAndReopen(player, uuid, done -> gui.getManager().setScreenOffset(uuid, periphId, sc.getOffsetX(), sc.getOffsetY() + offsetStep, sc.getOffsetZ(), done), GuiType.SCREEN_SETTINGS, periphId);
                case 30 -> updateAndReopen(player, uuid, done -> gui.getManager().setScreenOffset(uuid, periphId, sc.getOffsetX(), sc.getOffsetY(), sc.getOffsetZ() + offsetStep, done), GuiType.SCREEN_SETTINGS, periphId);
                case 31 -> updateAndReopen(player, uuid, done -> gui.getManager().resetScreenOffset(uuid, periphId, done), GuiType.SCREEN_SETTINGS, periphId);
                case 32 -> updateAndReopen(player, uuid, done -> gui.getManager().snapScreenOffset(uuid, periphId, done), GuiType.SCREEN_SETTINGS, periphId);
                case 33 -> {
                    float dx = (float)(player.getX() - snap.getX());
                    float dy = (float)(player.getY() - snap.getY());
                    float dz = (float)(player.getZ() - snap.getZ());
                    updateAndReopen(player, uuid, done -> gui.getManager().setScreenOffset(uuid, periphId, dx, dy, dz, done), GuiType.SCREEN_SETTINGS, periphId);
                }
                case 37 -> {
                    float[] euler = top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[0] += rotStep;
                    float[] q = top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    updateAndReopen(player, uuid, done -> gui.getManager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done), GuiType.SCREEN_SETTINGS, periphId);
                }
                case 38 -> {
                    float[] euler = top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[1] += rotStep;
                    float[] q = top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    updateAndReopen(player, uuid, done -> gui.getManager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done), GuiType.SCREEN_SETTINGS, periphId);
                }
                case 39 -> {
                    float[] euler = top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil.toEuler(sc.getOffsetRx(), sc.getOffsetRy(), sc.getOffsetRz(), sc.getOffsetRw());
                    euler[2] += rotStep;
                    float[] q = top.tobyprime.mcedia_mtv_plugin.util.EulerAngleUtil.toQuaternion(euler[0], euler[1], euler[2]);
                    updateAndReopen(player, uuid, done -> gui.getManager().setScreenRotation(uuid, periphId, q[0], q[1], q[2], q[3], done), GuiType.SCREEN_SETTINGS, periphId);
                }
                case 40 -> updateAndReopen(player, uuid, done -> gui.getManager().resetScreenRotation(uuid, periphId, done), GuiType.SCREEN_SETTINGS, periphId);
                case 49 -> gui.reopenPage(player, GuiType.PERIPHERAL_LIST, uuid);
                case 53 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.delete")) return;
                    updateAndReopen(player, uuid, done -> gui.getManager().removePeripheral(uuid, "screen", periphId, done), GuiType.PERIPHERAL_LIST, null);
                }
            }
        });
    }

    private void handleSpeakerSettings(Player player, MtvGui.MtvHolder holder, int slot, boolean rightClick, boolean shiftClick) {
        var uuid = holder.getEntityUuid();
        var periphId = holder.getPeripheralId();
        if (uuid == null || periphId == null) return;

        read(player, uuid, snap -> {
            if (snap == null) return;
            var sp = snap.findSpeaker(periphId);
            if (sp == null) return;

            float dir = rightClick ? 1 : -1;
            float fine = dir * 0.1F;
            float coarse = dir * 1.0F;
            float volumeStep = shiftClick ? coarse : fine;
            float rangeStep = dir * (shiftClick ? 5 : 1);
            float offsetStep = shiftClick ? coarse : fine;

            switch (slot) {
                case 10 -> updateAndReopen(player, uuid, done -> gui.getManager().setSpeakerVolume(uuid, periphId, sp.getVolume() + volumeStep, done), GuiType.SPEAKER_SETTINGS, periphId);
                case 11 -> updateAndReopen(player, uuid, done -> gui.getManager().setSpeakerRange(uuid, periphId, sp.getMaxRange() + rangeStep, done), GuiType.SPEAKER_SETTINGS, periphId);
                case 12 -> {
                    String next = switch (sp.getChannelMode()) {
                        case "left" -> "right";
                        case "right" -> "mix";
                        default -> "left";
                    };
                    updateAndReopen(player, uuid, done -> gui.getManager().setSpeakerChannelMode(uuid, periphId, next, done), GuiType.SPEAKER_SETTINGS, periphId);
                }
                case 13 -> updateAndReopen(player, uuid, done -> gui.getManager().resetSpeakerAudio(uuid, periphId, done), GuiType.SPEAKER_SETTINGS, periphId);
                case 28 -> updateAndReopen(player, uuid, done -> gui.getManager().setSpeakerOffset(uuid, periphId, sp.getOffsetX() + offsetStep, sp.getOffsetY(), sp.getOffsetZ(), done), GuiType.SPEAKER_SETTINGS, periphId);
                case 29 -> updateAndReopen(player, uuid, done -> gui.getManager().setSpeakerOffset(uuid, periphId, sp.getOffsetX(), sp.getOffsetY() + offsetStep, sp.getOffsetZ(), done), GuiType.SPEAKER_SETTINGS, periphId);
                case 30 -> updateAndReopen(player, uuid, done -> gui.getManager().setSpeakerOffset(uuid, periphId, sp.getOffsetX(), sp.getOffsetY(), sp.getOffsetZ() + offsetStep, done), GuiType.SPEAKER_SETTINGS, periphId);
                case 31 -> updateAndReopen(player, uuid, done -> gui.getManager().resetSpeakerOffset(uuid, periphId, done), GuiType.SPEAKER_SETTINGS, periphId);
                case 32 -> updateAndReopen(player, uuid, done -> gui.getManager().snapSpeakerOffset(uuid, periphId, done), GuiType.SPEAKER_SETTINGS, periphId);
                case 33 -> {
                    float dx = (float)(player.getX() - snap.getX());
                    float dy = (float)(player.getY() - snap.getY());
                    float dz = (float)(player.getZ() - snap.getZ());
                    updateAndReopen(player, uuid, done -> gui.getManager().setSpeakerOffset(uuid, periphId, dx, dy, dz, done), GuiType.SPEAKER_SETTINGS, periphId);
                }
                case 49 -> gui.reopenPage(player, GuiType.PERIPHERAL_LIST, uuid);
                case 53 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.delete")) return;
                    updateAndReopen(player, uuid, done -> gui.getManager().removePeripheral(uuid, "speaker", periphId, done), GuiType.PERIPHERAL_LIST, null);
                }
            }
        });
    }

    private void read(Player player, UUID uuid, Consumer<ManagedMtvPlayer> done) {
        gui.getManager().readSnapshot(uuid, snapshot -> delay(player, () -> {
            if (snapshot == null) {
                player.closeInventory();
                return;
            }
            done.accept(snapshot);
        }));
    }

    private void updateAndReopen(Player player, UUID uuid, Consumer<Consumer<Boolean>> update, MtvGui.GuiType page, String periphId) {
        update.accept(success -> {
            if (!Boolean.TRUE.equals(success)) {
                return;
            }
            delay(player, () -> gui.reopenPage(player, page, uuid, periphId));
        });
    }

    private void delay(Player player, Runnable task) {
        player.getScheduler().run(gui.getPlugin(), t -> task.run(), null);
    }

    private static int indexOf(int[] arr, int val) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == val) return i;
        return -1;
    }
}
