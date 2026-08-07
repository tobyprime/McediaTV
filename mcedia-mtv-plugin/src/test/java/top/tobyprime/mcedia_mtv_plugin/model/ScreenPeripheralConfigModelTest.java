package top.tobyprime.mcedia_mtv_plugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ScreenPeripheralConfigModelTest {
    @Test
    void passiveCoreProgressBarRemainsDisabledForMtvScreens() {
        var screen = new ScreenPeripheralConfigModel("screen_0");

        screen.setProgressBarVisible(true);
        screen.resetBasic();

        assertFalse(screen.isProgressBarVisible());
    }
}
