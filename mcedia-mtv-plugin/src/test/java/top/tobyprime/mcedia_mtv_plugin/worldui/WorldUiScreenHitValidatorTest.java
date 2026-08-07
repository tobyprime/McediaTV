package top.tobyprime.mcedia_mtv_plugin.worldui;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.model.ScreenPeripheralConfigModel;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiScreenHitValidatorTest {
    @Test
    void serverTransformMapsCenterAndBottomRightFromOnlySavedScreenData() {
        var target = new ManagedMtvPlayer();
        target.setUuid(UUID.randomUUID());
        target.setWorld("world");
        target.setX(10.0D);
        target.setY(64.0D);
        target.setZ(10.0D);
        target.setYaw(0.0F);
        target.setPitch(0.0F);
        var screen = new ScreenPeripheralConfigModel("main");
        screen.setOffsetY(0.5F);
        screen.setWidth(2.0F);
        screen.setHeight(1.0F);
        target.getScreens().add(screen);

        Vector center = WorldUiScreenHitValidator.screenPoint(target, screen, 0.5F, 0.5F);
        Vector bottomRight = WorldUiScreenHitValidator.screenPoint(target, screen, 1.0F, 1.0F);

        assertEquals(new Vector(10.0D, 64.5D, 10.0D), center);
        assertEquals(new Vector(11.0D, 64.0D, 10.0D), bottomRight);
    }

    @Test
    void outOfRangeUvIsRejectedBeforeAnyWorldRayTrace() {
        assertFalse(WorldUiScreenHitValidator.isUnitUv(-0.01F, 0.5F));
        assertFalse(WorldUiScreenHitValidator.isUnitUv(0.5F, 1.01F));
        assertTrue(WorldUiScreenHitValidator.isUnitUv(0.0F, 1.0F));
    }
}
