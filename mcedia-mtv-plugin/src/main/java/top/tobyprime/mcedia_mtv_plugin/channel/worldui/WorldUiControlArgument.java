package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import java.util.List;

public sealed interface WorldUiControlArgument permits WorldUiControlArgument.None,
        WorldUiControlArgument.PositionUs, WorldUiControlArgument.Scalar,
        WorldUiControlArgument.PlaylistIndex, WorldUiControlArgument.MediaUrl,
        WorldUiControlArgument.PlayOrderMode, WorldUiControlArgument.BooleanValue,
        WorldUiControlArgument.MediaUrlList {
    enum None implements WorldUiControlArgument {
        INSTANCE
    }

    record PositionUs(long value) implements WorldUiControlArgument { }

    record Scalar(float value) implements WorldUiControlArgument { }

    record PlaylistIndex(int value) implements WorldUiControlArgument { }

    record MediaUrl(String value) implements WorldUiControlArgument { }

    record PlayOrderMode(String value) implements WorldUiControlArgument { }

    record BooleanValue(boolean value) implements WorldUiControlArgument { }

    record MediaUrlList(List<String> urls) implements WorldUiControlArgument { }
}
