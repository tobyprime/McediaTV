package top.tobyprime.mcedia_mtv.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.tobyprime.mcedia_mtv.client.worldui.MtvWorldUiInputHook;

/** Suppresses vanilla attack only for a left-click already consumed by an MTV screen control. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftWorldUiInput {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void mcediaMtv$consumeWorldUiAttack(CallbackInfoReturnable<Boolean> callback) {
        if (MtvWorldUiInputHook.consumesAttack()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void mcediaMtv$consumeHeldWorldUiAttack(boolean down, CallbackInfo callback) {
        if (MtvWorldUiInputHook.consumesAttack()) {
            callback.cancel();
        }
    }
}
