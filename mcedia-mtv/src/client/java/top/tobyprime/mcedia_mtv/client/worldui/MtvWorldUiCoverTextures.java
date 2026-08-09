package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.metadata.MtvCoverTextureRegistry;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCover;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCoverCache;

import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Render-thread bridge from the bounded Cover byte cache to dynamic textures. */
final class MtvWorldUiCoverTextures {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvWorldUiCoverTextures.class);
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final Set<String> LOGGED_COVER_PROBLEMS = ConcurrentHashMap.newKeySet();
    private static final MtvCoverTextureRegistry<Identifier> TEXTURES = new MtvCoverTextureRegistry<>(64,
            textureId -> Minecraft.getInstance().getTextureManager().release(textureId));

    private MtvWorldUiCoverTextures() { }

    static Identifier texture(String coverUrl) {
        MtvMediaCover cover = MtvMediaCoverCache.getInstance().cached(coverUrl);
        if (cover == null) {
            // Still loading or never requested; the download layer logs its own result.
            return null;
        }
        if (cover.status() != MtvMediaCover.Status.RESOLVED) {
            if (LOGGED_COVER_PROBLEMS.add(coverUrl)) {
                LOGGER.debug("MTV cover unusable: url={}, reason={}", coverUrl, cover.errorReason());
            }
            return null;
        }
        Identifier existing = TEXTURES.get(cover.url());
        if (existing != null) return existing;
        if (!Minecraft.getInstance().isSameThread()) return null;
        try (var input = new ByteArrayInputStream(cover.bytes())) {
            NativeImage image = NativeImage.read(input);
            Identifier textureId = Identifier.fromNamespaceAndPath("mcedia_mtv",
                    "dynamic/world_ui_cover_" + NEXT_ID.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(textureId,
                    new DynamicTexture(() -> "mtv-world-ui-cover", image));
            TEXTURES.put(cover.url(), textureId);
            return textureId;
        } catch (Exception e) {
            if (LOGGED_COVER_PROBLEMS.add(coverUrl)) {
                LOGGER.info("MTV cover texture creation failed: url={}, reason={}", coverUrl, String.valueOf(e.getMessage()));
            }
            return null;
        }
    }

    static void clear() {
        LOGGED_COVER_PROBLEMS.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            TEXTURES.clear();
        } else {
            minecraft.execute(TEXTURES::clear);
        }
    }
}
