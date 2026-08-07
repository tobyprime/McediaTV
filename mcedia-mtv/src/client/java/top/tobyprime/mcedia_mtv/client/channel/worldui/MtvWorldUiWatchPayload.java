package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiWatchPayload(WorldUiWatchRequest request) implements CustomPacketPayload {
    public static final Type<MtvWorldUiWatchPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_world_ui_watch"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiWatchPayload> CODEC = CustomPacketPayload.codec(MtvWorldUiWatchPayload::write, MtvWorldUiWatchPayload::new);

    private MtvWorldUiWatchPayload(FriendlyByteBuf buffer) { this(MtvChannelProtocol.readWatchRequest(buffer)); }
    private void write(FriendlyByteBuf buffer) { MtvChannelProtocol.writeWatchRequest(buffer, request); }
    @Override public Type<MtvWorldUiWatchPayload> type() { return TYPE; }
}
