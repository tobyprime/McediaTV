package top.tobyprime.mcedia_mtv.client.metadata;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MtvMediaCoverCacheTest {
    private static final Executor DIRECT = Runnable::run;

    @Test
    void decodesValidImageAndPreservesDimensions() throws Exception {
        var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        var cover = MtvMediaCoverCache.decode("https://example.test/cover.png", output.toByteArray());

        assertEquals(MtvMediaCover.Status.RESOLVED, cover.status());
        assertEquals(3, cover.width());
        assertEquals(2, cover.height());
    }

    @Test
    void rejectsNonImageBytes() {
        assertThrows(Exception.class, () -> MtvMediaCoverCache.decode("https://example.test/cover", new byte[] {1, 2, 3}));
    }

    @Test
    void blankUrlFailsWithoutStartingARequest() {
        var cache = new MtvMediaCoverCache(2, DIRECT);
        var result = cache.loadAsync(" ").join();
        assertEquals(MtvMediaCover.Status.FAILED, result.status());
    }
}
