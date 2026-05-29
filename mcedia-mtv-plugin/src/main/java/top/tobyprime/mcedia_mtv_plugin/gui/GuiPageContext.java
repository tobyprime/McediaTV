package top.tobyprime.mcedia_mtv_plugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.selector.MtvPlayerSelector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Facade over MtvGui for use by GuiPage implementations.
 * Provides access to services, navigation helpers, and async utilities.
 */
public class GuiPageContext {
    private final MtvGui gui;

    GuiPageContext(MtvGui gui) {
        this.gui = gui;
    }

    public MtvGui gui() {
        return gui;
    }

    public JavaPlugin plugin() {
        return gui.getPlugin();
    }

    public MtvPlayerManager manager() {
        return gui.getManager();
    }

    public MtvPeripheralController controller() {
        return gui.getController();
    }

    public MtvPlaybackController playbackController() {
        return gui.getPlaybackController();
    }

    public MtvPlayerSelector selector() {
        return gui.getSelector();
    }

    // ─────────────────────────────────────────────────────────
    //  Navigation
    // ─────────────────────────────────────────────────────────

    /** Navigate to a page with full state. */
    public void navigateTo(Player player, MtvGui.GuiType type,
                           UUID entityUuid, String peripheralId, Map<String, String> state) {
        gui.navigateTo(player, type, entityUuid, peripheralId, state);
    }

    /** Navigate to a page with entity binding. */
    public void navigateTo(Player player, MtvGui.GuiType type, UUID entityUuid) {
        gui.navigateTo(player, type, entityUuid, null, null);
    }

    /** Navigate to a simple page. */
    public void navigateTo(Player player, MtvGui.GuiType type) {
        gui.navigateTo(player, type, null, null, null);
    }

    /** Re-render the current page without pushing history. */
    public void refresh(Player player) {
        gui.refreshCurrentPage(player);
    }

    // ─────────────────────────────────────────────────────────
    //  State access
    // ─────────────────────────────────────────────────────────

    /** Get the current page entry for a player, or null. */
    public PageEntry getCurrentEntry(Player player) {
        NavigationState nav = gui.getNavigation(player);
        return nav != null ? nav.getCurrent() : null;
    }

    /** Read a state value from the current page entry. */
    public String getState(Player player, String key, String def) {
        PageEntry entry = getCurrentEntry(player);
        return entry != null ? entry.getState().getOrDefault(key, def) : def;
    }

    /** Create a fresh mutable state map (convenience for building state). */
    public Map<String, String> newState() {
        return new HashMap<>();
    }

    /** Close inventory, show a prompt, and mark the player as awaiting chat input. */
    public void requestInput(Player player, String prompt, String kind) {
        player.closeInventory();
        player.sendMessage(prompt);
        gui.setAwaitingInput(player, kind);
    }

    /** Store awaiting-input marker so chat handler picks it up. */
    public void setAwaitingInput(Player player, String kind) {
        gui.setAwaitingInput(player, kind);
    }

    // ─────────────────────────────────────────────────────────
    //  Async helpers
    // ─────────────────────────────────────────────────────────

    public void runOnPlayer(Player player, Runnable task) {
        gui.runOnPlayerTask(player, task);
    }

    public void read(Player player, UUID uuid, Consumer<ManagedMtvPlayer> done) {
        gui.getManager().readSnapshot(uuid, snapshot -> delay(player, () -> {
            if (snapshot == null) {
                player.closeInventory();
                return;
            }
            done.accept(snapshot);
        }));
    }

    /** Perform an update, then refresh the current page on success. */
    public void updateAndRefresh(Player player, UUID uuid, Consumer<Consumer<Boolean>> update) {
        update.accept(success -> {
            if (!Boolean.TRUE.equals(success)) return;
            delay(player, () -> gui.refreshCurrentPage(player));
        });
    }

    public void delay(Player player, Runnable task) {
        player.getScheduler().run(gui.getPlugin(), scheduledTask -> task.run(), null);
    }
}
