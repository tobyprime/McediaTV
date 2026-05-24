package top.tobyprime.mcedia_mtv_plugin.util;

public final class MediaUrlNormalizer {
    private MediaUrlNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim();
    }
}
