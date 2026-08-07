package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiUnwatchPayload(WorldUiWatchRequest request) implements CustomPacketPayload {
    public static final Type<MtvWorldUiUnwatchPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_world_ui_unwatch"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiUnwatchPayload> CODEC = CustomPacketPayload.codec(MtvWorldUiUnwatchPayload::write, MtvWorldUiUnwatchPayload::new);

    private MtvWorldUiUnwatchPayload(FriendlyByteBuf buffer) { this(MtvChannelProtocol.readWatchRequest(buffer)); }
    private void write(FriendlyByteBuf buffer) { MtvChannelProtocol.writeWatchRequest(buffer, request); }
    @Override public Type<MtvWorldUiUnwatchPayload> type() { return TYPE; }
}
