package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Base class for all MTV GUI pages.
 * <p>
 *   Row 0 (slots 0-8) is the navigation title bar, managed entirely by this base class.
 *   Rows 1-5 (slots 9-53) are filled by each page via {@link #renderPage}.
 * </p>
 *
 * <h3>Sync vs async pages</h3>
 *   Sync pages build the inventory and return from {@link #renderPage} with it open.
 *   Async pages start an async operation in {@link #renderPage}, then in the callback
 *   create the inventory, fill content, call {@link #setupTitleBar} and open.
 */
public abstract class GuiPage {
    // PDC keys written to the page icon (currently written for auditing; not read back)
    protected static final NamespacedKey KEY_PAGE_TYPE   = new NamespacedKey("mcediamtv", "page_type");

    // ─────────────────────────────────────────────────────────
    //  Page metadata
    // ─────────────────────────────────────────────────────────

    public abstract MtvGui.GuiType type();

    public Component getTitle(PageEntry entry) { return Component.text("MTV"); }

    public Material icon() { return Material.PAPER; }

    // ─────────────────────────────────────────────────────────
    //  Entry points
    // ─────────────────────────────────────────────────────────

    /** Navigate to this page — push to history stack then render. */
    public void open(Player player, GuiPageContext context, PageEntry entry) {
        NavigationState nav = context.gui().getOrCreateNavigation(player);
        nav.navigate(entry);
        renderPage(player, context, nav, entry);
    }

    /** Render this page from a saved entry (back/forward/refresh — no history change). */
    public void renderFromEntry(Player player, GuiPageContext context,
                                NavigationState nav, PageEntry entry) {
        renderPage(player, context, nav, entry);
    }

    protected abstract void renderPage(Player player, GuiPageContext context,
                                       NavigationState nav, PageEntry entry);

    // ─────────────────────────────────────────────────────────
    //  Helpers for renderPage
    // ─────────────────────────────────────────────────────────

    protected final Inventory createInventory(PageEntry entry) {
        return Bukkit.createInventory(
                new MtvGui.MtvHolder(type(), entry.getEntityUuid(), entry.getPeripheralId()),
                54, getTitle(entry));
    }

    /** Open the inventory for the player. */
    protected final void openInventory(Player player, Inventory inv) {
        player.openInventory(inv);
    }

    /** Set up the navigation title bar (row 0). Call after filling content. */
    protected final void setupTitleBar(Inventory inv, NavigationState nav, PageEntry entry) {
        ItemStack gray = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var grayMeta = gray.getItemMeta();
        grayMeta.displayName(Component.text(" "));
        gray.setItemMeta(grayMeta);

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, gray.clone());
        }
        if (nav.canGoBack()) {
            inv.setItem(0, navButton("← 上一页"));
        }
        inv.setItem(4, createPageIcon(entry, nav));
        if (nav.canGoForward()) {
            inv.setItem(8, navButton("→ 下一页"));
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Click handling
    // ─────────────────────────────────────────────────────────

    public final boolean handleClick(Player player, GuiPageContext context, int slot,
                                     boolean rightClick, boolean shiftClick) {
        NavigationState nav = context.gui().getNavigation(player);
        if (slot == 0 && nav != null && nav.canGoBack()) {
            context.gui().restoreNavigation(player, nav.goBack());
            return true;
        }
        if (slot == 8 && nav != null && nav.canGoForward()) {
            context.gui().restoreNavigation(player, nav.goForward());
            return true;
        }
        if (slot < 9) return false;
        PageEntry entry = nav != null ? nav.getCurrent() : null;
        return handleContentClick(player, context, entry, slot, rightClick, shiftClick);
    }

    protected abstract boolean handleContentClick(Player player, GuiPageContext context,
                                                   PageEntry entry, int slot,
                                                   boolean rightClick, boolean shiftClick);

    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        return false;
    }

    // ─────────────────────────────────────────────────────────
    //  Shared utilities
    // ─────────────────────────────────────────────────────────

    protected static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore).map(Component::text).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static int indexOf(int[] arr, int val) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == val) return i;
        return -1;
    }

    // ─────────────────────────────────────────────────────────
    //  Internal
    // ─────────────────────────────────────────────────────────

    private ItemStack createPageIcon(PageEntry entry, NavigationState nav) {
        ItemStack stack = new ItemStack(icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(getTitle(entry));

        // Store the page type in PDC for inventory identification
        meta.getPersistentDataContainer().set(
                KEY_PAGE_TYPE, PersistentDataType.STRING, entry.getType().name());

        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack navButton(String name) {
        ItemStack stack = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        stack.setItemMeta(meta);
        return stack;
    }
}
