package com.kevin.tiertagger.mixin;

import com.kevin.tiertagger.TierTagger;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
    @Unique
    private static final AtomicBoolean hasCheckedVersion = new AtomicBoolean(false);

    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void showUpdateScreen(CallbackInfo ci) {
        if (hasCheckedVersion.get()) return;

        Version currentVersion = FabricLoader.getInstance().getModContainer("tier-tagger")
                .map(m -> m.getMetadata().getVersion()).orElse(null);
        Version latestVersion = TierTagger.getLatestVersion();

        if (TierTagger.isObsolete()) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(
                    b -> {
                        if (b) {
                            Minecraft.getInstance().stop();
                        } else {
                            Minecraft.getInstance().setScreen(this);
                        }
                    },
                    Component.translatable("tiertagger.obsolete.title"),
                    Component.translatable("tiertagger.obsolete.desc"),
                    Component.translatable("menu.quit"),
                    Component.translatable("tiertagger.outdated.ignore")
            ));
        } else if (currentVersion != null && latestVersion != null && currentVersion.compareTo(latestVersion) < 0) {
            Component newVersion = Component.literal(latestVersion.getFriendlyString()).withStyle(ChatFormatting.GREEN);

            Minecraft.getInstance().setScreen(new ConfirmScreen(
                    b -> {
                        if (b) {
                            String url = "https://modrinth.com/mod/tiertagger/version/" + latestVersion.getFriendlyString();
                            Util.getPlatform().openUri(url);
                        }

                        Minecraft.getInstance().setScreen(this);
                    },
                    Component.translatable("tiertagger.outdated.title"),
                    Component.translatable("tiertagger.outdated.desc", newVersion),
                    Component.translatable("tiertagger.outdated.download"),
                    Component.translatable("tiertagger.outdated.ignore")
            ));
        }

        hasCheckedVersion.set(true);
    }
}
