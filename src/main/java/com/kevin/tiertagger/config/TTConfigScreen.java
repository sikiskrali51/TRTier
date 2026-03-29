package com.kevin.tiertagger.config;

import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.model.TierList;
import com.kevin.tiertagger.tierlist.PlayerSearchScreen;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.option.*;
import net.uku3lig.ukulib.config.option.widget.ButtonTab;
import net.uku3lig.ukulib.config.screen.TabbedConfigScreen;
import net.uku3lig.ukulib.utils.Ukutils;

import java.util.*;
import java.util.stream.Collectors;

public class TTConfigScreen extends TabbedConfigScreen<TierTaggerConfig> {
    public TTConfigScreen(Screen parent) {
        super("TierTagger Config", parent, TierTagger.getManager());
    }

    @Override
    protected Tab[] getTabs(TierTaggerConfig config) {
        return new Tab[]{new MainSettingsTab(), new ColorsTab(), new TierlistTab()};
    }

    public class MainSettingsTab extends ButtonTab<TierTaggerConfig> {
        public MainSettingsTab() {
            super("tiertagger.config", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    CyclingOption.ofBoolean("tiertagger.config.enabled", config.isEnabled(), config::setEnabled),
                    new CyclingOption<>("tiertagger.config.gamemode", TierCache.getGamemodes(), config.getGameMode(), m -> config.setGameMode(m.id()), m -> Component.literal(m.title()),
                            m -> m.isNone() ? Tooltip.create(Component.translatable("tiertagger.config.gamemode.none")) : null),
                    CyclingOption.ofBoolean("tiertagger.config.retired", config.isShowRetired(), config::setShowRetired),
                    CyclingOption.ofTranslatableEnum("tiertagger.config.highest", TierTaggerConfig.HighestMode.class, config.getHighestMode(), config::setHighestMode),
                    CyclingOption.ofBoolean("tiertagger.config.icons", config.isShowIcons(), config::setShowIcons),
                    CyclingOption.ofBoolean("tiertagger.config.playerList", config.isPlayerList(), config::setPlayerList),
                    new SimpleButton("tiertagger.clear", b -> TierCache.clearCache()),
                    new ScreenOpenButton("tiertagger.config.search", PlayerSearchScreen::new)
            };
        }
    }

    public class TierlistTab extends ButtonTab<TierTaggerConfig> {
        public TierlistTab() {
            super("tiertagger.config.tierlists", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            Optional<TierList> current = TierList.findByUrl(config.getApiUrl());

            List<WidgetCreator> widgets = (List<WidgetCreator>) (List<?>) Arrays.stream(TierList.values())
                    .map(t -> {
                        boolean isCurrent = current.isPresent() && current.get() == t;
                        return new SimpleButton(t.styledName(isCurrent), b -> {
                            config.setApiUrl(t.getUrl());
                            TierTagger.getManager().saveConfig();
                            TTConfigScreen.this.onClose();
                            TierCache.init();
                            Ukutils.sendToast(Component.literal("Tierlist changed to " + t.getName() + "!"), Component.literal("Reloading tiers..."));
                        });
                    })
                    .collect(Collectors.toList());

            if (current.isEmpty()) {
                widgets.add(new SimpleButton("Custom (selected, " + config.getApiUrl() + ")", b -> {}));
            }

            return widgets.toArray(WidgetCreator[]::new);
        }
    }

    public class ColorsTab extends ButtonTab<TierTaggerConfig> {
        protected ColorsTab() {
            super("tiertagger.colors", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            // i genuinely don't understand but chaining the calls just EXPLODES????
            Comparator<Map.Entry<String, Integer>> comparator = Comparator.comparing(e -> e.getKey().charAt(2));
            comparator = comparator.thenComparing(e -> e.getKey().charAt(0));

            List<WidgetCreator> tiers = (List<WidgetCreator>) (List<?>) config.getTierColors().entrySet().stream()
                    .sorted(comparator)
                    .map(e -> new ColorOption(e.getKey(), e.getValue(), val -> config.getTierColors().put(e.getKey(), val)))
                    .collect(Collectors.toList());

            tiers.addLast(new ColorOption("tiertagger.colors.retired", config.getRetiredColor(), config::setRetiredColor));

            return tiers.toArray(WidgetCreator[]::new);
        }
    }
}
