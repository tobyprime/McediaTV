package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.UUID;

/** Authoritative, event-driven state needed to render world UI controls. */
public record WorldUiControlState(
        UUID mtvUuid,
        String channelId,
        float masterVolume,
        boolean canControl,
        long channelRevision
) {
    public WorldUiControlState {
        if (mtvUuid == null || channelId == null || channelId.isBlank()
                || !Float.isFinite(masterVolume) || masterVolume < 0.0F || masterVolume > 1.0F
                || channelRevision < 0L) {
            throw new IllegalArgumentException("world UI control state is invalid");
        }
    }
}
