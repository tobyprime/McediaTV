package top.tobyprime.mcedia_mtv_plugin.gui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Browser-style navigation history per player.
 * <p>
 *   backStack: pages the player came from (top = most recent).
 *   forwardStack: pages the player went back from (top = most recent).
 *   current: the page currently open.
 * </p>
 * <p>
 *   State lives server-side only in {@link MtvGui#playerNavs}.
 *   No PDC serialization is performed.
 * </p>
 */
public class NavigationState {
    private final Deque<PageEntry> backStack = new ArrayDeque<>();
    private final Deque<PageEntry> forwardStack = new ArrayDeque<>();
    private PageEntry current;

    public NavigationState(PageEntry initial) {
        this.current = initial;
    }

    /** Navigate to a new page — push current to back, clear forward. */
    public void navigate(PageEntry entry) {
        if (current != null) backStack.push(current);
        current = entry;
        forwardStack.clear();
    }

    /** Go back one step. Returns the (new) current entry, or null. */
    public PageEntry goBack() {
        if (backStack.isEmpty()) return null;
        if (current != null) forwardStack.push(current);
        current = backStack.pop();
        return current;
    }

    /** Go forward one step. Returns the (new) current entry, or null. */
    public PageEntry goForward() {
        if (forwardStack.isEmpty()) return null;
        if (current != null) backStack.push(current);
        current = forwardStack.pop();
        return current;
    }

    public PageEntry getCurrent() { return current; }
    public boolean canGoBack() { return !backStack.isEmpty(); }
    public boolean canGoForward() { return !forwardStack.isEmpty(); }

    public static NavigationState fromEntry(PageEntry entry) {
        return new NavigationState(entry);
    }
}
