package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.metadata.MtvCoverTextureRegistry;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCover;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCoverCache;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicLong;

/** 26.1 render-thread bridge from the bounded Cover byte cache to dynamic textures. */
final class MtvWorldUiCoverTextures {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final MtvCoverTextureRegistry<Identifier> TEXTURES = new MtvCoverTextureRegistry<>(64,
            textureId -> Minecraft.getInstance().getTextureManager().release(textureId));

    private MtvWorldUiCoverTextures() { }

    static Identifier texture(String coverUrl) {
        MtvMediaCover cover = MtvMediaCoverCache.getInstance().cached(coverUrl);
        if (cover == null || cover.status() != MtvMediaCover.Status.RESOLVED) return null;
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
        } catch (Exception ignored) {
            return null;
        }
    }

    static void clear() {
        TEXTURES.clear();
    }
}
