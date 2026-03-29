package com.kevin.tiertagger.tierlist;

import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.screen.CloseableScreen;
import org.jetbrains.annotations.NotNull;

public class PlayerInfoScreen extends CloseableScreen {
    private final PlayerInfo info;
    private final PlayerSkinWidget skin;

    public PlayerInfoScreen(Screen parent, PlayerInfo info, PlayerSkinWidget skin) {
        super(Component.literal("Player Info"), parent);
        this.info = info;
        this.skin = skin;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
                .build());

        this.addRenderableWidget(this.skin);

        int rankingHeight = this.info.rankings().size() * 11;
        int infoHeight = 56; // 4 lines of text (10 px tall) + 6 px padding
        int startY = (this.height - infoHeight - rankingHeight) / 2;
        int rankingY = startY + infoHeight;

        for (PlayerInfo.NamedRanking namedRanking : this.info.getSortedTiers()) {
            // ugly "fix" to avoid crashes if upstream doesn't have the right names
            if (namedRanking.mode() == null) continue;

            StringWidget text = new StringWidget(formatTier(namedRanking.mode(), namedRanking.ranking()), this.font);
            text.setX(this.width / 2 + 5);
            text.setY(rankingY);

            // TRTier doesn't provide attained date, so just show points
            Component tooltipText = Component.literal("Points: " + points(namedRanking.ranking())).withStyle(ChatFormatting.GRAY);
            text.setTooltip(Tooltip.create(tooltipText));
            this.addRenderableWidget(text);
            rankingY += 11;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.info.name() + "'s profile", this.width / 2, 20, 0xFFFFFFFF);

        int rankingHeight = this.info.rankings().size() * 11;
        int infoHeight = 56; // 4 lines of text (10 px tall) + 6 px padding
        int startY = (this.height - infoHeight - rankingHeight) / 2;

        graphics.drawString(this.font, getRegionText(this.info), this.width / 2 + 5, startY, 0xFFFFFFFF);
        graphics.drawString(this.font, getPointsText(this.info), this.width / 2 + 5, startY + 15, 0xFFFFFFFF);
        graphics.drawString(this.font, getRankText(this.info), this.width / 2 + 5, startY + 30, 0xFFFFFFFF);
        graphics.drawString(this.font, "Rankings:", this.width / 2 + 5, startY + 45, 0xFFFFFFFF);
    }

    private Component formatTier(@NotNull GameMode gamemode, PlayerInfo.Ranking ranking) {
        Component tierText = TierTagger.getRankingText(ranking, true);

        return Component.empty()
                .append(gamemode.asStyled(true))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(tierText);
    }

    private Component getRegionText(PlayerInfo info) {
        return Component.empty()
                .append(Component.literal("Region: "))
                .append(Component.literal(info.region()).withStyle(s -> s.withColor(info.getRegionColor())));
    }

    private Component getPointsText(PlayerInfo info) {
        PlayerInfo.PointInfo pointInfo = info.getPointInfo();

        return Component.empty()
                .append(Component.literal("Points: "))
                .append(Component.literal(info.points() + " ").withStyle(s -> s.withColor(pointInfo.getColor())))
                .append(Component.literal("(" + pointInfo.getTitle() + ")").withStyle(s -> s.withColor(pointInfo.getAccentColor())));
    }

    private Component getRankText(PlayerInfo info) {
        int color = switch (info.overall()) {
            case 1 -> 0xe5ba43;
            case 2 -> 0x808c9c;
            case 3 -> 0xb56326;
            default -> 0x1e2634;
        };

        return Component.empty()
                .append(Component.literal("Global rank: "))
                .append(Component.literal("#" + info.overall()).withStyle(s -> s.withColor(color)));
    }

    private int points(PlayerInfo.Ranking ranking) {
        return switch (ranking.tier()) {
            case 1 -> ranking.pos() == 0 ? 60 : 45;
            case 2 -> ranking.pos() == 0 ? 30 : 20;
            case 3 -> ranking.pos() == 0 ? 10 : 6;
            case 4 -> ranking.pos() == 0 ? 4 : 3;
            case 5 -> ranking.pos() == 0 ? 2 : 1;
            default -> 0;
        };
    }
}
