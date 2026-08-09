package top.tobyprime.mcedia_mtv.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia_mtv.client.worldui.MtvWorldUiRenderer;

/**
 * Renders the MTV-owned screen layer (video, strips, danmaku, status) with the
 * same submit collector the level renderer uses, keeping the screen geometry
 * identical to what core rendered before.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void mcediaMtv$submitWorldScreens(PoseStack poseStack, LevelRenderState levelRenderState,
                                              SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        MtvWorldUiRenderer.onLevelSubmit(poseStack, levelRenderState, submitNodeCollector);
    }
}
