package top.tobyprime.mcedia_mtv.client.metadata;

public record MtvMediaCover(
        String url,
        byte[] bytes,
        int width,
        int height,
        Status status,
        String errorReason
) {
    public enum Status { RESOLVED, FAILED }

    public MtvMediaCover {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    public static MtvMediaCover failed(String url, String reason) {
        return new MtvMediaCover(url == null ? "" : url, new byte[0], 0, 0, Status.FAILED,
                reason == null || reason.isBlank() ? "cover unavailable" : reason);
    }
}
