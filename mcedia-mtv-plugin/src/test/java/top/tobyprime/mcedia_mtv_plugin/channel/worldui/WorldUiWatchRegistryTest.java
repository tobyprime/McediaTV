package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiWatchRegistryTest {
    @Test
    void eachPlayerWatchesOnlyOneMtvAndChangingTargetRemovesOldWatch() {
        var registry = new WorldUiWatchRegistry();
        var player = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        registry.watch(player, first);
        registry.watch(player, second);

        assertTrue(registry.watchers(first).isEmpty());
        assertEquals(Set.of(player), registry.watchers(second));
    }

    @Test
    void removingAnMtvClearsEveryWatcherForThatTarget() {
        var registry = new WorldUiWatchRegistry();
        var firstPlayer = UUID.randomUUID();
        var secondPlayer = UUID.randomUUID();
        var mtv = UUID.randomUUID();

        registry.watch(firstPlayer, mtv);
        registry.watch(secondPlayer, mtv);
        registry.unwatchMtv(mtv);

        assertTrue(registry.watchers(mtv).isEmpty());
    }
}
