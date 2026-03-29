package com.kevin.tiertagger.model;

import com.kevin.tiertagger.TierTagger;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public record GameMode(String id, String title) {
    public static final GameMode NONE = new GameMode("annoying_long_id_that_no_one_will_ever_use_just_to_make_sure", "§cNone§r");

    /**
     * TRTier has fixed kits, no dynamic mode list endpoint.
     * Returns a hardcoded list of all available kits on trtier.com.
     */
    public static CompletableFuture<List<GameMode>> fetchGamemodes(java.net.http.HttpClient client) {
        List<GameMode> modes = List.of(
                new GameMode("nethpot", "NethPot"),
                new GameMode("sword", "Sword"),
                new GameMode("pot", "Pot"),
                new GameMode("smp", "SMP"),
                new GameMode("crystal", "Crystal"),
                new GameMode("axe", "Axe"),
                new GameMode("uhc", "UHC"),
                new GameMode("mace", "Mace"),
                new GameMode("diasmp", "DiaSMP"),
                new GameMode("ogv", "OGV")
        );
        return CompletableFuture.completedFuture(modes);
    }

    public boolean isNone() {
        return this.id.equals(NONE.id);
    }

    private Pair<Character, TextColor> iconAndColor() {
        return switch (this.id) {
            case "axe" -> Pair.of('\uE701', TextColor.fromLegacyFormat(ChatFormatting.GREEN));
            case "mace" -> Pair.of('\uE702', TextColor.fromLegacyFormat(ChatFormatting.GRAY));
            case "nethpot", "neth_pot" -> Pair.of('\uE703', TextColor.fromRgb(0x7d4a40));
            case "pot" -> Pair.of('\uE704', TextColor.fromRgb(0xff0000));
            case "smp" -> Pair.of('\uE705', TextColor.fromRgb(0xeccb45));
            case "sword" -> Pair.of('\uE706', TextColor.fromRgb(0xa4fdf0));
            case "uhc" -> Pair.of('\uE707', TextColor.fromLegacyFormat(ChatFormatting.RED));
            case "crystal" -> Pair.of('\uE708', TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE));
            case "diasmp", "dia_smp" -> Pair.of('\uE806', TextColor.fromRgb(0x8c668b));
            case "ogv", "og_vanilla" -> Pair.of('\uE810', TextColor.fromLegacyFormat(ChatFormatting.GOLD));
            default -> Pair.of('•', TextColor.fromLegacyFormat(ChatFormatting.WHITE));
        };
    }

    public Optional<Character> icon() {
        Pair<Character, TextColor> pair = this.iconAndColor();

        return pair.right().getValue() == 0xFFFFFF ? Optional.empty() : Optional.of(pair.left());
    }

    public Component asStyled(boolean withDefaultDot) {
        Pair<Character, TextColor> pair = this.iconAndColor();

        if (pair.right().getValue() == 0xFFFFFF && !withDefaultDot) {
            return Component.literal(this.title);
        } else {
            Component name = Component.literal(this.title).withStyle(s -> s.withColor(pair.right()));
            return Component.literal(pair.left() + " ").append(name);
        }
    }
}
