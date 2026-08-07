package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiPlaylistPageRequestPayload(WorldUiPlaylistPageRequest request) implements CustomPacketPayload {
    public static final Type<MtvWorldUiPlaylistPageRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_playlist_page_request"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiPlaylistPageRequestPayload> CODEC =
            CustomPacketPayload.codec(MtvWorldUiPlaylistPageRequestPayload::write, MtvWorldUiPlaylistPageRequestPayload::new);

    private MtvWorldUiPlaylistPageRequestPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readPlaylistPageRequest(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writePlaylistPageRequest(buffer, request);
    }

    @Override
    public Type<MtvWorldUiPlaylistPageRequestPayload> type() {
        return TYPE;
    }
}
