package com.kevin.tiertagger.mixin;

import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.config.TierTaggerConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    @Nullable
    public Component prependTier(Component original, PlayerInfo entry) {
        TierTaggerConfig config = TierTagger.getManager().getConfig();
        if (config.isEnabled() && config.isPlayerList()) {
            return TierTagger.appendTier(entry.getProfile().id(), entry.getProfile().name(), original);
        } else {
            return original;
        }
    }
}
