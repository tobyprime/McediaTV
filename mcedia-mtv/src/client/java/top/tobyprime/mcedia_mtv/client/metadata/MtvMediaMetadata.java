package top.tobyprime.mcedia_mtv.client.metadata;

public record MtvMediaMetadata(
        String normalizedUrl,
        String title,
        String author,
        String description,
        String platform,
        String coverUrl,
        Status status,
        String errorReason
) {
    public enum Status {
        RESOLVED,
        FAILED
    }

    public static MtvMediaMetadata failed(String normalizedUrl, String errorReason) {
        return new MtvMediaMetadata(normalizedUrl, "", "", "", "", "", Status.FAILED, errorReason == null ? "unknown error" : errorReason);
    }
}
