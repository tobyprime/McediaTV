package top.tobyprime.mcedia_mtv.client.mixin;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display.ItemDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

@Mixin(DisplayRenderer.ItemDisplayRenderer.class)
public class MixinItemDisplayRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void clearItemForMcediaDisplay(ItemDisplay entity, ItemDisplayEntityRenderState state, float partialTicks, CallbackInfo ci) {
        var stack = entity.getSlot(0).get();
        if (stack.isEmpty()) return;
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return;
        var tag = customData.copyTag();
        if (tag.contains(EntityPlayerManager.ENTITY_CONFIG_KEY)
                || tag.contains(EntityPlayerManager.CONFIG_KEY)
                || tag.contains(EntityPlayerManager.LEGACY_CONFIG_KEY)) {
            state.item.clear();
        }
    }
}
