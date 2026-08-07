package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

public record WorldUiControlResult(
        long requestId,
        boolean accepted,
        WorldUiControlError error,
        long revision
) {
    public static WorldUiControlResult accepted(long requestId, long revision) {
        return new WorldUiControlResult(requestId, true, WorldUiControlError.NONE, revision);
    }

    public static WorldUiControlResult rejected(long requestId, WorldUiControlError error, long revision) {
        return new WorldUiControlResult(requestId, false, error, Math.max(0L, revision));
    }
}
