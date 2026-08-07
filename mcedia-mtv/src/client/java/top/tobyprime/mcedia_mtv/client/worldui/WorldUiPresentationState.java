package top.tobyprime.mcedia_mtv.client.worldui;

import java.util.Objects;

/**
 * Mapping-independent presentation selection for the world UI.  It deliberately
 * contains no Minecraft client objects so hover state cannot generate packets.
 */
public final class WorldUiPresentationState {
    private final WorldUiLayout layout = new WorldUiLayout();

    private static final float FADE_IN_STEP = 0.25F;
    private static final float FADE_OUT_STEP = 0.15F;

    private WorldUiInteractionState.Target hoveredTarget;
    private float hoveredU;
    private float hoveredV;
    private WorldUiInteractionState.Target expandedTarget;
    private boolean playlistExpanded;
    private int playlistStart;
    private WorldUiInteractionState.Target fadeTarget;
    private float hoverFade;

    public void update(WorldUiInteractionState.Target target, float u, float v) {
        hoveredTarget = target;
        hoveredU = u;
        hoveredV = v;
        fadeTarget = target;
        if (sameScreen(expandedTarget, target)) {
            expandedTarget = target;
        }
    }

    public void clearHover() {
        hoveredTarget = null;
    }

    public void expand(WorldUiInteractionState.Target target) {
        expandedTarget = Objects.requireNonNull(target, "target");
        playlistExpanded = false;
        playlistStart = 0;
        hoverFade = 1.0F;
    }

    public void collapse() {
        expandedTarget = null;
        playlistExpanded = false;
        playlistStart = 0;
        fadeTarget = null;
        hoverFade = 0.0F;
    }

    /** Steps the collapsed toggle alpha toward visible or hidden once per tick. */
    public void tickHoverFade(boolean hovering) {
        if (hovering) {
            hoverFade = Math.min(1.0F, hoverFade + FADE_IN_STEP);
        } else {
            hoverFade = Math.max(0.0F, hoverFade - FADE_OUT_STEP);
            if (hoverFade <= 0.0F) {
                fadeTarget = null;
            }
        }
    }

    public float hoverFade() {
        return hoverFade;
    }

    /** True while the pointer is inside the bottom-right collapsed toggle trigger. */
    public boolean hoveringToggleTrigger() {
        return hoveredTarget != null && layout.isToggleTrigger(hoveredU, hoveredV);
    }

    public boolean isExpanded(WorldUiInteractionState.Target target) {
        return sameScreen(target, expandedTarget);
    }

    public boolean shouldRender(WorldUiInteractionState.Target target) {
        if (target == null) {
            return false;
        }
        return isExpanded(target)
                || (sameScreen(target, hoveredTarget) && layout.isToggleTrigger(hoveredU, hoveredV))
                || (sameScreen(target, fadeTarget) && hoverFade > 0.0F);
    }

    public WorldUiHit hit() {
        if (hoveredTarget == null) {
            return WorldUiHit.NONE;
        }
        WorldUiHit hit = layout.hit(hoveredU, hoveredV, isExpanded(hoveredTarget), playlistExpanded);
        return switch (hit.kind()) {
            case PLAYLIST_ITEM, REMOVE_ITEM, MOVE_FRONT, MOVE_BACK -> new WorldUiHit(hit.kind(), playlistStart + hit.index());
            default -> hit;
        };
    }

    public WorldUiInteractionState.Target hoveredTarget() {
        return hoveredTarget;
    }

    public float hoveredU() {
        return hoveredU;
    }

    public float hoveredV() {
        return hoveredV;
    }

    public boolean isPlaylistExpanded() {
        return playlistExpanded;
    }

    public void togglePlaylist() {
        playlistExpanded = !playlistExpanded;
        if (!playlistExpanded) playlistStart = 0;
    }

    public int playlistStart() {
        return playlistStart;
    }

    public void previousPlaylistPage() {
        playlistStart = Math.max(0, playlistStart - 8);
    }

    public void nextPlaylistPage(int itemCount) {
        int lastStart = Math.max(0, ((Math.max(0, itemCount) - 1) / 8) * 8);
        playlistStart = Math.min(lastStart, playlistStart + 8);
    }

    private static boolean sameScreen(WorldUiInteractionState.Target first, WorldUiInteractionState.Target second) {
        return first != null && second != null
                && first.mtvUuid().equals(second.mtvUuid())
                && first.screenId().equals(second.screenId())
                && first.channelId().equals(second.channelId());
    }
}
