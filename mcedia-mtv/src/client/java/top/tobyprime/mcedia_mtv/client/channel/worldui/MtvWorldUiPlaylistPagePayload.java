package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiPlaylistPagePayload(WorldUiPlaylistPage page) implements CustomPacketPayload {
    public static final Type<MtvWorldUiPlaylistPagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_playlist_page"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiPlaylistPagePayload> CODEC =
            CustomPacketPayload.codec(MtvWorldUiPlaylistPagePayload::write, MtvWorldUiPlaylistPagePayload::new);

    private MtvWorldUiPlaylistPagePayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readPlaylistPage(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writePlaylistPage(buffer, page);
    }

    @Override
    public Type<MtvWorldUiPlaylistPagePayload> type() {
        return TYPE;
    }
}
