package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import org.bukkit.entity.Player;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelPlayOrderMode;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelProtocol;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelService;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.util.MediaUrlNormalizer;
import top.tobyprime.mcedia_mtv_plugin.worldui.WorldUiScreenHitValidator;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Authoritative entry point for MTV world-screen controls. Network handlers
 * decode a bounded request before calling this class; this class then verifies
 * the current entity snapshot and never trusts client-side channel selection.
 */
public final class WorldUiControlDispatcher {
    private static final long MAX_SEEK_US = 7L * 24L * 60L * 60L * 1_000_000L;

    private final MtvPlayerManager manager;
    private final MtvChannelService channelService;
    private final WorldUiRateLimiter rateLimiter;
    private final WorldUiScreenHitValidator hitValidator;

    public WorldUiControlDispatcher(MtvPlayerManager manager, WorldUiRateLimiter rateLimiter) {
        this.manager = manager;
        this.channelService = manager.getChannelService();
        this.rateLimiter = rateLimiter == null ? new WorldUiRateLimiter() : rateLimiter;
        this.hitValidator = new WorldUiScreenHitValidator();
    }

    public WorldUiControlDispatcher(MtvPlayerManager manager) {
        this(manager, new WorldUiRateLimiter());
    }

    /**
     * Resolves the target on its entity scheduler, then completes exactly once
     * with the authoritative outcome. This avoids reading MTV entity data from
     * the plugin-message thread on Folia.
     */
    public void dispatch(Player player, WorldUiControlRequest request, Consumer<WorldUiControlResult> done) {
        Consumer<WorldUiControlResult> resultConsumer = done == null ? ignored -> { } : done;
        WorldUiControlResult malformed = validateRequest(request);
        if (malformed != null) {
            resultConsumer.accept(malformed);
            return;
        }
        if (player == null) {
            resultConsumer.accept(rejected(request, WorldUiControlError.PERMISSION_DENIED, 0L));
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), WorldUiRateLimiter.RequestType.CONTROL)) {
            resultConsumer.accept(rejected(request, WorldUiControlError.RATE_LIMITED, 0L));
            return;
        }

        manager.withManagedPlayer(request.targetMtvUuid(), target -> {
            dispatchResolved(player, target, request, resultConsumer);
            return Boolean.TRUE;
        }, found -> {
            if (!Boolean.TRUE.equals(found)) {
                resultConsumer.accept(rejected(request, WorldUiControlError.TARGET_NOT_FOUND, 0L));
            }
        });
    }

    /**
     * Package-visible for focused validation tests. The supplied updater must
     * persist master volume against the MTV entity and return its success.
     */
    void dispatchResolved(Player player, ManagedMtvPlayer target, WorldUiControlRequest request,
                          Consumer<WorldUiControlResult> done) {
        long revision = revisionFor(target, request);
        WorldUiControlError targetError = validateTarget(player, target, request);
        if (targetError != WorldUiControlError.NONE) {
            done.accept(rejected(request, targetError, revision));
            return;
        }
        var hitValidation = hitValidator.validate(player, target, request.screenId(), request.hitU(), request.hitV());
        if (!hitValidation.accepted()) {
            done.accept(rejected(request, hitValidation.error(), revision));
            return;
        }

        MtvChannelBinding binding = channelService.resolveBinding(target);
        ChannelRuntimeState state = channelService.ensureChannelState(binding.channelId());
        if (state == null) {
            done.accept(rejected(request, WorldUiControlError.CHANNEL_MISMATCH, 0L));
            return;
        }
        revision = state.getRevision();
        if (!binding.channelId().equals(request.channelId())) {
            done.accept(rejected(request, WorldUiControlError.CHANNEL_MISMATCH, revision));
            return;
        }
        if (!channelService.canControlChannelPlayback(player, state)) {
            done.accept(rejected(request, WorldUiControlError.PERMISSION_DENIED, revision));
            return;
        }
        if (request.operation().changesChannelRevision() && request.expectedRevision() != revision) {
            done.accept(rejected(request, WorldUiControlError.STALE_REVISION, revision));
            return;
        }

        WorldUiControlError argumentError = validateArgument(request, state);
        if (argumentError != WorldUiControlError.NONE) {
            done.accept(rejected(request, argumentError, revision));
            return;
        }

        if (request.operation() == WorldUiControlOperation.SET_MASTER_VOLUME) {
            float volume = ((WorldUiControlArgument.Scalar) request.argument()).value();
            setMasterVolume(target, request, volume, revision, done);
            return;
        }
        if (request.operation() == WorldUiControlOperation.TOGGLE_MUTE) {
            float volume = target.getMasterVolume() <= 0.0F ? 1.0F : 0.0F;
            setMasterVolume(target, request, volume, revision, done);
            return;
        }

        boolean changed = executeChannelOperation(player, request, binding.channelId());
        long latestRevision = channelService.ensureChannelState(binding.channelId()).getRevision();
        done.accept(changed
                ? WorldUiControlResult.accepted(request.requestId(), latestRevision)
                : rejected(request, WorldUiControlError.INVALID_ARGUMENT, latestRevision));
    }

    private void setMasterVolume(ManagedMtvPlayer target, WorldUiControlRequest request, float volume,
                                 long revision, Consumer<WorldUiControlResult> done) {
        if (Float.compare(target.getMasterVolume(), volume) == 0) {
            done.accept(WorldUiControlResult.accepted(request.requestId(), revision));
            return;
        }
        manager.setMasterVolume(request.targetMtvUuid(), volume, updated -> done.accept(Boolean.TRUE.equals(updated)
                ? WorldUiControlResult.accepted(request.requestId(), revision)
                : rejected(request, WorldUiControlError.INTERNAL_ERROR, revision)));
    }

    private boolean executeChannelOperation(Player player, WorldUiControlRequest request, String channelId) {
        return switch (request.operation()) {
            case TOGGLE_PAUSE -> channelService.togglePause(channelId);
            case SEEK_ABSOLUTE -> channelService.updateStartAt(channelId,
                    ((WorldUiControlArgument.PositionUs) request.argument()).value());
            case SEEK_RELATIVE -> channelService.seekRelative(channelId,
                    ((WorldUiControlArgument.PositionUs) request.argument()).value());
            case SET_SPEED -> channelService.updateSpeed(channelId,
                    ((WorldUiControlArgument.Scalar) request.argument()).value());
            case PLAY_INDEX -> channelService.playPlaylistIndex(channelId,
                    ((WorldUiControlArgument.PlaylistIndex) request.argument()).value());
            case NEXT -> channelService.playNextManual(channelId);
            case PREVIOUS -> channelService.playPreviousManual(channelId);
            case PREPEND -> channelService.prependPlaylistItem(channelId,
                    normalizedUrl((WorldUiControlArgument.MediaUrl) request.argument()));
            case APPEND -> channelService.appendPlaylistItem(channelId,
                    normalizedUrl((WorldUiControlArgument.MediaUrl) request.argument()));
            case INSERT_NEXT -> channelService.insertNextPlaylistItem(channelId,
                    normalizedUrl((WorldUiControlArgument.MediaUrl) request.argument()));
            case INSERT_AND_PLAY -> channelService.insertNextAndPlay(channelId,
                    normalizedUrl((WorldUiControlArgument.MediaUrl) request.argument()));
            case REMOVE -> channelService.removePlaylistItem(channelId,
                    ((WorldUiControlArgument.PlaylistIndex) request.argument()).value());
            case MOVE_FRONT -> channelService.movePlaylistItemToFront(channelId,
                    ((WorldUiControlArgument.PlaylistIndex) request.argument()).value());
            case MOVE_BACK -> channelService.movePlaylistItemToBack(channelId,
                    ((WorldUiControlArgument.PlaylistIndex) request.argument()).value());
            case CLEAR -> channelService.clearPlaylist(player, channelId);
            case SET_PLAY_ORDER -> channelService.setPlayOrderMode(channelId,
                    parsePlayOrderMode(((WorldUiControlArgument.PlayOrderMode) request.argument()).value()));
            case SET_MASTER_VOLUME, TOGGLE_MUTE -> throw new IllegalStateException("entity volume operation was not handled");
        };
    }

    private WorldUiControlError validateTarget(Player player, ManagedMtvPlayer target, WorldUiControlRequest request) {
        if (target == null || target.getUuid() == null || !request.targetMtvUuid().equals(target.getUuid())) {
            return WorldUiControlError.TARGET_NOT_FOUND;
        }
        if (!target.isPowered()) {
            return WorldUiControlError.TARGET_NOT_FOUND;
        }
        if (target.findScreen(request.screenId()) == null) {
            return WorldUiControlError.SCREEN_NOT_FOUND;
        }
        if (player.getWorld() == null || target.getWorld() == null
                || !target.getWorld().equals(player.getWorld().getName())) {
            return WorldUiControlError.WORLD_MISMATCH;
        }
        return WorldUiControlError.NONE;
    }

    static WorldUiControlError validateArgument(WorldUiControlRequest request, ChannelRuntimeState state) {
        return switch (request.operation()) {
            case TOGGLE_PAUSE, NEXT, PREVIOUS, CLEAR, TOGGLE_MUTE -> request.argument() == WorldUiControlArgument.None.INSTANCE
                    ? WorldUiControlError.NONE : WorldUiControlError.INVALID_ARGUMENT;
            case SEEK_ABSOLUTE -> validateAbsoluteSeek(request.argument(), state);
            case SEEK_RELATIVE -> validateRelativeSeek(request.argument());
            case SET_SPEED -> validateRange(request.argument(), 0.25F, 4.0F);
            case SET_MASTER_VOLUME -> validateRange(request.argument(), 0.0F, 1.0F);
            case PLAY_INDEX, REMOVE, MOVE_FRONT, MOVE_BACK -> validatePlaylistIndex(request.argument(), state);
            case PREPEND, APPEND, INSERT_NEXT, INSERT_AND_PLAY -> validMediaUrl(request.argument())
                    ? WorldUiControlError.NONE : WorldUiControlError.INVALID_ARGUMENT;
            case SET_PLAY_ORDER -> playOrderMode(request.argument()) == null
                    ? WorldUiControlError.INVALID_ARGUMENT : WorldUiControlError.NONE;
        };
    }

    private static WorldUiControlError validateAbsoluteSeek(WorldUiControlArgument argument, ChannelRuntimeState state) {
        if (!(argument instanceof WorldUiControlArgument.PositionUs position) || position.value() < 0L || position.value() > MAX_SEEK_US) {
            return WorldUiControlError.INVALID_ARGUMENT;
        }
        long durationUs = state.getDurationMs() * 1_000L;
        return durationUs > 0L && position.value() > durationUs ? WorldUiControlError.INVALID_ARGUMENT : WorldUiControlError.NONE;
    }

    private static WorldUiControlError validateRelativeSeek(WorldUiControlArgument argument) {
        if (!(argument instanceof WorldUiControlArgument.PositionUs position)
                || position.value() < -MAX_SEEK_US || position.value() > MAX_SEEK_US) {
            return WorldUiControlError.INVALID_ARGUMENT;
        }
        return WorldUiControlError.NONE;
    }

    private static WorldUiControlError validateRange(WorldUiControlArgument argument, float min, float max) {
        if (!(argument instanceof WorldUiControlArgument.Scalar scalar) || !Float.isFinite(scalar.value())
                || scalar.value() < min || scalar.value() > max) {
            return WorldUiControlError.INVALID_ARGUMENT;
        }
        return WorldUiControlError.NONE;
    }

    private static WorldUiControlError validatePlaylistIndex(WorldUiControlArgument argument, ChannelRuntimeState state) {
        if (!(argument instanceof WorldUiControlArgument.PlaylistIndex index)
                || index.value() < 0 || index.value() >= state.getPlaylist().size()) {
            return WorldUiControlError.INVALID_ARGUMENT;
        }
        return WorldUiControlError.NONE;
    }

    private static boolean validMediaUrl(WorldUiControlArgument argument) {
        if (!(argument instanceof WorldUiControlArgument.MediaUrl mediaUrl)) {
            return false;
        }
        String normalized = MediaUrlNormalizer.normalize(mediaUrl.value());
        return !normalized.isBlank() && normalized.length() <= MtvChannelProtocol.MAX_MEDIA_URL_LENGTH;
    }

    private static String normalizedUrl(WorldUiControlArgument.MediaUrl argument) {
        return MediaUrlNormalizer.normalize(argument.value());
    }

    private static ChannelPlayOrderMode parsePlayOrderMode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ChannelPlayOrderMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ChannelPlayOrderMode playOrderMode(WorldUiControlArgument argument) {
        return argument instanceof WorldUiControlArgument.PlayOrderMode mode
                ? parsePlayOrderMode(mode.value()) : null;
    }

    static WorldUiControlResult validateRequest(WorldUiControlRequest request) {
        if (request == null || request.targetMtvUuid() == null || request.operation() == null || request.argument() == null
                || request.requestId() < 0L || request.expectedRevision() < 0L
                || request.screenId() == null || request.screenId().isBlank() || request.screenId().length() > 128
                || request.channelId() == null || request.channelId().isBlank() || request.channelId().length() > 256
                || !Float.isFinite(request.hitU()) || !Float.isFinite(request.hitV())
                || request.hitU() < 0.0F || request.hitU() > 1.0F || request.hitV() < 0.0F || request.hitV() > 1.0F) {
            return WorldUiControlResult.rejected(requestId(request), WorldUiControlError.MALFORMED_REQUEST, 0L);
        }
        return null;
    }

    private long revisionFor(ManagedMtvPlayer target, WorldUiControlRequest request) {
        if (target == null || target.getUuid() == null || !target.getUuid().equals(request.targetMtvUuid())) {
            return 0L;
        }
        var state = channelService.ensureChannelState(channelService.resolveBinding(target).channelId());
        return state == null ? 0L : state.getRevision();
    }

    private static long requestId(WorldUiControlRequest request) {
        return request == null ? 0L : Math.max(0L, request.requestId());
    }

    private static WorldUiControlResult rejected(WorldUiControlRequest request, WorldUiControlError error, long revision) {
        return WorldUiControlResult.rejected(requestId(request), error, revision);
    }
}
