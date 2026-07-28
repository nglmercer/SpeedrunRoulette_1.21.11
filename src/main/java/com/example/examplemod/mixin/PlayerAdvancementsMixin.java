package com.example.examplemod.mixin;

import com.example.examplemod.event.AdvancementCompletedCallback;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("RETURN"))
    private void speedrun$onAdvancementAward(AdvancementHolder advancement, String criterionKey, CallbackInfoReturnable<Boolean> cir) {
        Boolean changed = cir.getReturnValue();
        if (changed != null && changed && getOrStartProgress(advancement).isDone()) {
            AdvancementCompletedCallback.EVENT.invoker().onCompleted(this.player, advancement);
        }
    }
}
