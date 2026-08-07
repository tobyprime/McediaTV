package top.tobyprime.mcedia_mtv.client.channel.worldui;

import top.tobyprime.mcedia_mtv.client.channel.MtvChannelClientPacketSender;

public final class WorldUiPlaylistPageSender {
    private WorldUiPlaylistPageSender() {
    }

    public static boolean request(String channelId, long knownRevision, int offset) {
        if (!WorldUiCapabilityState.getInstance().supported()) {
            return false;
        }
        MtvChannelClientPacketSender.send(new MtvWorldUiPlaylistPageRequestPayload(
                new WorldUiPlaylistPageRequest(channelId, knownRevision, offset)));
        return true;
    }
}
