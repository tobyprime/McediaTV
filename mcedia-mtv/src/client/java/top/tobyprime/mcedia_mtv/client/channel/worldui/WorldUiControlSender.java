package top.tobyprime.mcedia_mtv.client.channel.worldui;

import top.tobyprime.mcedia_mtv.client.channel.MtvChannelClientPacketSender;
import top.tobyprime.mcedia_mtv.client.worldui.WorldUiInteractionState;

/** Capability-gated bridge from local world UI state to custom payloads. */
public final class WorldUiControlSender implements WorldUiInteractionState.ControlSender {
    private static final WorldUiControlSender INSTANCE = new WorldUiControlSender();

    private volatile WorldUiControlResult lastResult;

    private WorldUiControlSender() {
    }

    public static WorldUiControlSender getInstance() {
        return INSTANCE;
    }

    @Override
    public void send(WorldUiControlRequest request) {
        if (request == null || !WorldUiCapabilityState.getInstance().supported()) {
            return;
        }
        MtvChannelClientPacketSender.send(new MtvWorldUiControlRequestPayload(request));
    }

    @Override
    public void watch(WorldUiInteractionState.Target target) {
        // Watch registration is added by the version-specific screen adapter.
    }

    @Override
    public void unwatch(WorldUiInteractionState.Target target) {
        // Watch registration is added by the version-specific screen adapter.
    }

    public void onResult(WorldUiControlResult result) {
        lastResult = result;
    }

    public WorldUiControlResult lastResult() {
        return lastResult;
    }
}
