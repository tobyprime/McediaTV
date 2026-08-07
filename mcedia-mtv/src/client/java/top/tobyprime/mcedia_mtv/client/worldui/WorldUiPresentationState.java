package top.tobyprime.mcedia_mtv.client.worldui;

import java.util.Objects;

/**
 * Mapping-independent presentation selection for the world UI.  It deliberately
 * contains no Minecraft client objects so hover state cannot generate packets.
 */
public final class WorldUiPresentationState {
    private final WorldUiLayout layout = new WorldUiLayout();

    private WorldUiInteractionState.Target hoveredTarget;
    private float hoveredU;
    private float hoveredV;
    private WorldUiInteractionState.Target expandedTarget;

    public void update(WorldUiInteractionState.Target target, float u, float v) {
        hoveredTarget = target;
        hoveredU = u;
        hoveredV = v;
        if (sameScreen(expandedTarget, target)) {
            expandedTarget = target;
        }
    }

    public void clearHover() {
        hoveredTarget = null;
    }

    public void expand(WorldUiInteractionState.Target target) {
        expandedTarget = Objects.requireNonNull(target, "target");
    }

    public void collapse() {
        expandedTarget = null;
    }

    public boolean isExpanded(WorldUiInteractionState.Target target) {
        return sameScreen(target, expandedTarget);
    }

    public boolean shouldRender(WorldUiInteractionState.Target target) {
        if (target == null) {
            return false;
        }
        return isExpanded(target) || (sameScreen(target, hoveredTarget) && layout.hit(hoveredU, hoveredV, false).isToggle());
    }

    public WorldUiHit hit() {
        if (hoveredTarget == null) {
            return WorldUiHit.NONE;
        }
        return layout.hit(hoveredU, hoveredV, isExpanded(hoveredTarget));
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

    private static boolean sameScreen(WorldUiInteractionState.Target first, WorldUiInteractionState.Target second) {
        return first != null && second != null
                && first.mtvUuid().equals(second.mtvUuid())
                && first.screenId().equals(second.screenId())
                && first.channelId().equals(second.channelId());
    }
}
