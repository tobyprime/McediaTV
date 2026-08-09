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
    void reEncodesJpegCoversAsPngSoMinecraftCanLoadThem() throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);

        var cover = MtvMediaCoverCache.decode("https://example.test/cover.jpg", output.toByteArray());

        assertEquals(MtvMediaCover.Status.RESOLVED, cover.status());
        byte[] bytes = cover.bytes();
        assertEquals(0x89, bytes[0] & 0xFF);
        assertEquals('P', bytes[1]);
        assertEquals('N', bytes[2]);
        assertEquals('G', bytes[3]);
    }

    @Test
    void downscalesOversizedCoversToTheDimensionLimit() throws Exception {
        var image = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);

        var cover = MtvMediaCoverCache.decode("https://example.test/cover-large.jpg", output.toByteArray());

        assertEquals(MtvMediaCover.Status.RESOLVED, cover.status());
        assertEquals(1024, cover.width());
        assertEquals(683, cover.height());
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
