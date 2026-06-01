package top.tobyprime.mcedia_mtv_plugin.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.model.ScreenPeripheralConfigModel;
import top.tobyprime.mcedia_mtv_plugin.model.SpeakerPeripheralConfigModel;

import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 外设（屏幕/扬声器）控制逻辑的通用入口。
 * 封装了 periphId 解析（显式 ID 或默认取第一个）以及对外设的修改操作。
 * GUI 和命令层都通过此类与底层管理器交互。
 */
public class MtvPeripheralController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvPeripheralController.class);

    private final MtvPlayerManager manager;

    public MtvPeripheralController(MtvPlayerManager manager) {
        this.manager = manager;
    }

    public MtvPlayerManager getManager() {
        return manager;
    }

    /** 检查玩家是否有指定权限，无权限时自动发送提示并返回 false。 */
    public static boolean checkPerm(Player player, String perm) {
        if (!player.hasPermission(perm)) {
            player.sendMessage("你没有权限执行此操作。");
            return false;
        }
        return true;
    }

    /** 检查玩家是否可以编辑该 MTV 播放器（处理私有/公开模式）。 */
    public static boolean canEdit(Player player, ManagedMtvPlayer snapshot) {
        return MtvPlayerManager.canEditPlayer(player, snapshot);
    }

    // ====== Peripheral resolution ======

    /**
     * 解析屏幕 ID。如果传入了 periphId 且实体中存在该屏幕则直接返回，
     * 否则回退到第一个屏幕。无屏幕时返回 null。
     */
    public String resolveScreenId(ManagedMtvPlayer player, String periphId) {
        if (periphId != null) {
            if (player.findScreen(periphId) != null) return periphId;
            LOGGER.warn("Screen {} not found, falling back to first screen", periphId);
        }
        return player.getScreens().isEmpty() ? null : player.getScreens().get(0).getId();
    }

    /**
     * 解析扬声器 ID。如果传入了 periphId 且实体中存在该扬声器则直接返回，
     * 否则回退到第一个扬声器。无扬声器时返回 null。
     */
    public String resolveSpeakerId(ManagedMtvPlayer player, String periphId) {
        if (periphId != null) {
            if (player.findSpeaker(periphId) != null) return periphId;
            LOGGER.warn("Speaker {} not found, falling back to first speaker", periphId);
        }
        return player.getSpeakers().isEmpty() ? null : player.getSpeakers().get(0).getId();
    }

    // ====== Screen operations (with periphId resolution) ======

    public void setScreenSize(UUID uuid, ManagedMtvPlayer target, String periphId,
                              float w, float h, Consumer<Boolean> done) {
        String id = resolveScreenId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setScreenSize(uuid, id, w, h, done);
    }

    public void setScreenOffset(UUID uuid, ManagedMtvPlayer target, String periphId,
                                float x, float y, float z, Consumer<Boolean> done) {
        String id = resolveScreenId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setScreenOffset(uuid, id, x, y, z, done);
    }

    public void setScreenFillMode(UUID uuid, ManagedMtvPlayer target, String periphId,
                                  String mode, Consumer<Boolean> done) {
        String id = resolveScreenId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setScreenFillMode(uuid, id, mode, done);
    }

    public void setScreenDanmakuVisible(UUID uuid, ManagedMtvPlayer target, String periphId,
                                        boolean visible, Consumer<Boolean> done) {
        String id = resolveScreenId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setScreenDanmakuVisible(uuid, id, visible, done);
    }

    public void setScreenBrightness(UUID uuid, ManagedMtvPlayer target, String periphId,
                                    int value, Consumer<Boolean> done) {
        String id = resolveScreenId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setScreenBrightness(uuid, id, value, done);
    }

    public void setScreenRotation(UUID uuid, ManagedMtvPlayer target, String periphId,
                                  float rx, float ry, float rz, float rw, Consumer<Boolean> done) {
        String id = resolveScreenId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setScreenRotation(uuid, id, rx, ry, rz, rw, done);
    }

    // ====== Speaker operations (with periphId resolution) ======

    public void setSpeakerVolume(UUID uuid, ManagedMtvPlayer target, String periphId,
                                 float value, Consumer<Boolean> done) {
        String id = resolveSpeakerId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setSpeakerVolume(uuid, id, value, done);
    }

    public void setSpeakerRange(UUID uuid, ManagedMtvPlayer target, String periphId,
                                float value, Consumer<Boolean> done) {
        String id = resolveSpeakerId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setSpeakerRange(uuid, id, value, done);
    }

    public void setSpeakerOffset(UUID uuid, ManagedMtvPlayer target, String periphId,
                                 float x, float y, float z, Consumer<Boolean> done) {
        String id = resolveSpeakerId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.setSpeakerOffset(uuid, id, x, y, z, done);
    }

    // ====== Snap operations ======

    public void snapScreenOffset(UUID uuid, ManagedMtvPlayer target, String periphId,
                                 Consumer<Boolean> done) {
        String id = resolveScreenId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.snapScreenOffset(uuid, id, done);
    }

    public void snapSpeakerOffset(UUID uuid, ManagedMtvPlayer target, String periphId,
                                  Consumer<Boolean> done) {
        String id = resolveSpeakerId(target, periphId);
        if (id == null) { done.accept(false); return; }
        manager.snapSpeakerOffset(uuid, id, done);
    }

    public void snapEntityPosition(UUID uuid, Consumer<Boolean> done) {
        manager.snapEntityPosition(uuid, done);
    }

    // ====== Queries ======

    public List<String> getScreenIds(ManagedMtvPlayer player) {
        return player.getScreens().stream().map(ScreenPeripheralConfigModel::getId).toList();
    }

    public List<String> getSpeakerIds(ManagedMtvPlayer player) {
        return player.getSpeakers().stream().map(SpeakerPeripheralConfigModel::getId).toList();
    }

    public boolean hasScreens(ManagedMtvPlayer player) {
        return !player.getScreens().isEmpty();
    }

    public boolean hasSpeakers(ManagedMtvPlayer player) {
        return !player.getSpeakers().isEmpty();
    }
}
