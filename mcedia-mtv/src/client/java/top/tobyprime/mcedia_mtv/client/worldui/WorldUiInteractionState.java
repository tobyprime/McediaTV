package top.tobyprime.mcedia_mtv.client.worldui;

import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlArgument;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlOperation;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlRequest;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Keeps local hover, expansion and drag state independent of Minecraft mappings. */
public final class WorldUiInteractionState {
    private final ControlSender sender;
    private final AtomicLong requestIds = new AtomicLong();

    private Target expandedTarget;
    private Drag activeDrag = Drag.NONE;
    private float dragU;
    private float dragV;

    public WorldUiInteractionState(ControlSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public boolean isExpanded(Target target) {
        return target != null && target.equals(expandedTarget);
    }

    public void onPrimaryPress(Target target, WorldUiHit hit, float u, float v, long durationUs) {
        if (target == null || hit == null) {
            return;
        }
        if (hit.isToggle()) {
            toggle(target);
            return;
        }
        if (!isExpanded(target)) {
            return;
        }
        dragU = clamp(u);
        dragV = clamp(v);
        activeDrag = switch (hit.kind()) {
            case SEEK -> Drag.SEEK;
            case VOLUME -> Drag.VOLUME;
            default -> Drag.NONE;
        };
        if (activeDrag == Drag.NONE) {
            sendImmediate(target, hit, durationUs);
        }
    }

    public void onPointerMove(float u, float v) {
        if (activeDrag == Drag.NONE) {
            return;
        }
        dragU = clamp(u);
        dragV = clamp(v);
    }

    public void onPrimaryRelease(float u, float v, long durationUs) {
        if (activeDrag == Drag.NONE || expandedTarget == null) {
            return;
        }
        dragU = clamp(u);
        dragV = clamp(v);
        if (activeDrag == Drag.SEEK) {
            send(expandedTarget, dragU, dragV, WorldUiControlOperation.SEEK_ABSOLUTE,
                    new WorldUiControlArgument.PositionUs(Math.round(Math.max(0L, durationUs) * dragU)));
        } else {
            send(expandedTarget, dragU, dragV, WorldUiControlOperation.SET_MASTER_VOLUME,
                    new WorldUiControlArgument.Scalar(dragU));
        }
        activeDrag = Drag.NONE;
    }

    public void collapse() {
        if (expandedTarget != null) {
            sender.unwatch(expandedTarget);
        }
        expandedTarget = null;
        activeDrag = Drag.NONE;
    }

    private void toggle(Target target) {
        if (target.equals(expandedTarget)) {
            collapse();
            return;
        }
        collapse();
        expandedTarget = target;
        sender.watch(target);
    }

    private void sendImmediate(Target target, WorldUiHit hit, long durationUs) {
        WorldUiControlOperation operation = switch (hit.kind()) {
            case TOGGLE_PAUSE -> WorldUiControlOperation.TOGGLE_PAUSE;
            case NEXT -> WorldUiControlOperation.NEXT;
            case PREVIOUS -> WorldUiControlOperation.PREVIOUS;
            default -> null;
        };
        if (operation != null) {
            send(target, dragU, dragV, operation, WorldUiControlArgument.None.INSTANCE);
        }
    }

    private void send(Target target, float u, float v, WorldUiControlOperation operation, WorldUiControlArgument argument) {
        sender.send(new WorldUiControlRequest(target.mtvUuid(), target.screenId(), target.channelId(), requestIds.incrementAndGet(),
                target.revision(), u, v, operation, argument));
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    public record Target(UUID mtvUuid, String screenId, String channelId, long revision) {
        public Target {
            Objects.requireNonNull(mtvUuid, "mtvUuid");
            if (screenId == null || screenId.isBlank() || channelId == null || channelId.isBlank() || revision < 0L) {
                throw new IllegalArgumentException("target fields are invalid");
            }
        }
    }

    public interface ControlSender {
        void send(WorldUiControlRequest request);

        void watch(Target target);

        void unwatch(Target target);
    }

    private enum Drag {
        NONE,
        SEEK,
        VOLUME
    }
}
