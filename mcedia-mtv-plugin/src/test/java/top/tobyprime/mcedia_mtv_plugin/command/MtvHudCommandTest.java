package top.tobyprime.mcedia_mtv_plugin.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MtvHudCommandTest {

    @Test
    void commandClassInstantiable() {
        // Just verify the class loads and has expected structure
        assertDoesNotThrow(() -> {
            var constructors = MtvHudCommand.class.getConstructors();
            assertTrue(constructors.length > 0);
        });
    }
}
