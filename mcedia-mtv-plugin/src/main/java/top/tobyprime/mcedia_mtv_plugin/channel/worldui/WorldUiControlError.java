package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

/** Wire order is shared with the Fabric client. Do not reorder members. */
public enum WorldUiControlError {
    NONE,
    UNSUPPORTED_CLIENT,
    MALFORMED_REQUEST,
    TARGET_NOT_FOUND,
    SCREEN_NOT_FOUND,
    CHANNEL_MISMATCH,
    WORLD_MISMATCH,
    OCCLUDED,
    PERMISSION_DENIED,
    STALE_REVISION,
    INVALID_ARGUMENT,
    RATE_LIMITED,
    INTERNAL_ERROR
}
