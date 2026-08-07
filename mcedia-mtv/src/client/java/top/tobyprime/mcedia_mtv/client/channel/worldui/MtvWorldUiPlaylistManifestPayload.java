package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiPlaylistManifestPayload(WorldUiPlaylistManifest manifest) implements CustomPacketPayload {
    public static final Type<MtvWorldUiPlaylistManifestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_playlist_manifest"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiPlaylistManifestPayload> CODEC =
            CustomPacketPayload.codec(MtvWorldUiPlaylistManifestPayload::write, MtvWorldUiPlaylistManifestPayload::new);

    private MtvWorldUiPlaylistManifestPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readPlaylistManifest(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writePlaylistManifest(buffer, manifest);
    }

    @Override
    public Type<MtvWorldUiPlaylistManifestPayload> type() {
        return TYPE;
    }
}
