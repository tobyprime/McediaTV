package top.tobyprime.mcedia_mtv_plugin.manager;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv_plugin.model.ControlAccess;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtvPlayerManagerPermissionTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void controlAccessCyclesThroughThreeLevels() {
        assertEquals(ControlAccess.CONTROL, ControlAccess.PUBLIC.next());
        assertEquals(ControlAccess.PRIVATE, ControlAccess.CONTROL.next());
        assertEquals(ControlAccess.PUBLIC, ControlAccess.PRIVATE.next());
    }

    @Test
    void publicAndControlLevelsGrantPlaybackControl() {
        assertTrue(MtvPlayerManager.allowsControl(ControlAccess.PUBLIC));
        assertTrue(MtvPlayerManager.allowsControl(ControlAccess.CONTROL));
        assertFalse(MtvPlayerManager.allowsControl(ControlAccess.PRIVATE));
    }

    @Test
    void ownerAlwaysControlsRegardlessOfLevel() {
        var target = owned(ControlAccess.PRIVATE);
        assertTrue(MtvPlayerManager.canControlPlayer(player(OWNER, false), target));
        assertTrue(MtvPlayerManager.canEditPlayer(player(OWNER, false), target));
        assertTrue(MtvPlayerManager.canManagePlayer(player(OWNER, false), target));
    }

    @Test
    void unownedPlayerIsFullyAccessible() {
        var target = new ManagedMtvPlayer();
        target.setControlAccess(ControlAccess.PRIVATE);
        assertTrue(MtvPlayerManager.canControlPlayer(player(OTHER, false), target));
        assertTrue(MtvPlayerManager.canEditPlayer(player(OTHER, false), target));
        assertTrue(MtvPlayerManager.canManagePlayer(player(OTHER, false), target));
    }

    @Test
    void nonOwnerControlPermissionByLevel() {
        assertEquals(true, canControl(player(OTHER, false), ControlAccess.PUBLIC));
        assertEquals(true, canControl(player(OTHER, false), ControlAccess.CONTROL));
        assertEquals(false, canControl(player(OTHER, false), ControlAccess.PRIVATE));
        // 拥有 control.others 权限的玩家不受级别限制
        assertEquals(true, canControl(player(OTHER, true), ControlAccess.PRIVATE));
    }

    @Test
    void nonOwnerEditPermissionOnlyAtPublicLevel() {
        assertEquals(true, canEdit(player(OTHER, false), ControlAccess.PUBLIC));
        assertEquals(false, canEdit(player(OTHER, false), ControlAccess.CONTROL));
        assertEquals(false, canEdit(player(OTHER, false), ControlAccess.PRIVATE));
        // 拥有 edit.others 权限的玩家不受级别限制
        assertEquals(true, canEdit(player(OTHER, true), ControlAccess.PRIVATE));
    }

    @Test
    void nonOwnerCannotDeleteOrChangePermissionAtAnyLevel() {
        assertEquals(false, canManage(player(OTHER, false), ControlAccess.PUBLIC));
        assertEquals(false, canManage(player(OTHER, false), ControlAccess.CONTROL));
        assertEquals(false, canManage(player(OTHER, false), ControlAccess.PRIVATE));
        // 仅拥有 edit.others 权限的玩家可管理
        assertEquals(true, canManage(player(OTHER, true), ControlAccess.PRIVATE));
    }

    @Test
    void nullPlayerIsDeniedForOwnedPlayer() {
        var target = owned(ControlAccess.PUBLIC);
        assertFalse(MtvPlayerManager.canControlPlayer(null, target));
        assertFalse(MtvPlayerManager.canEditPlayer(null, target));
        assertFalse(MtvPlayerManager.canManagePlayer(null, target));
    }

    private static ManagedMtvPlayer owned(ControlAccess access) {
        var target = new ManagedMtvPlayer();
        target.setOwner(OWNER);
        target.setControlAccess(access);
        return target;
    }

    private static boolean canControl(Player player, ControlAccess access) {
        return MtvPlayerManager.canControlPlayer(player, owned(access));
    }

    private static boolean canEdit(Player player, ControlAccess access) {
        return MtvPlayerManager.canEditPlayer(player, owned(access));
    }

    private static boolean canManage(Player player, ControlAccess access) {
        return MtvPlayerManager.canManagePlayer(player, owned(access));
    }

    private static Player player(UUID uuid, boolean hasPermission) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) {
                        return uuid;
                    }
                    if ("hasPermission".equals(method.getName())) {
                        return hasPermission;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    if (returnType == double.class) {
                        return 0.0D;
                    }
                    if (returnType == float.class) {
                        return 0.0F;
                    }
                    if (returnType == short.class) {
                        return (short) 0;
                    }
                    if (returnType == byte.class) {
                        return (byte) 0;
                    }
                    if (returnType == char.class) {
                        return (char) 0;
                    }
                    return null;
                });
    }
}
