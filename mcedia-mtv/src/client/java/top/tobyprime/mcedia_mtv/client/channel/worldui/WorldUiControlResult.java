package top.tobyprime.mcedia_mtv.client.channel.worldui;

public record WorldUiControlResult(
        long requestId,
        boolean accepted,
        WorldUiControlError error,
        long revision
) {
}
