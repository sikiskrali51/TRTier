package com.kevin.tiertagger.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.TierTagger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public record PlayerInfo(String uuid, String name, Map<String, Ranking> rankings, String region, int points,
                         int overall, List<Badge> badges, @SerializedName("combat_master") boolean combatMaster) {
    public record Ranking(int tier, int pos, @Nullable @SerializedName("peak_tier") Integer peakTier,
                          @Nullable @SerializedName("peak_pos") Integer peakPos, long attained,
                          boolean retired) {

        /**
         * Lower is better.
         */
        public int comparableTier() {
            return tier * 2 + pos;
        }

        /**
         * Lower is better.
         */
        public int comparablePeak() {
            if (peakTier == null || peakPos == null) {
                return Integer.MAX_VALUE;
            } else {
                return peakTier * 2 + peakPos;
            }
        }

        public NamedRanking asNamed(GameMode mode) {
            return new NamedRanking(mode, this);
        }
    }

    public record NamedRanking(@Nullable GameMode mode, Ranking ranking) {
    }

    public record Badge(String title, String desc) {
    }

    private static final Map<String, Integer> REGION_COLORS = Map.of(
            "TR", 0xff6a6e,
            "EU", 0x6aff6e,
            "NA", 0xff6a6e,
            "SA", 0xff9900,
            "AS", 0xc27ba0,
            "ME", 0xffd966,
            "AF", 0x674ea7,
            "RU", 0x6a8fff
    );

    /**
     * Parse a TRTier tier string like "HT1", "LT3" into tier number and pos.
     * H = pos 0 (High), L = pos 1 (Low).
     */
    private static Ranking parseTierString(String tierStr) {
        if (tierStr == null || tierStr.length() < 3) return null;
        int pos = tierStr.charAt(0) == 'H' ? 0 : 1; // HT = High (pos 0), LT = Low (pos 1)
        int tier;
        try {
            tier = Integer.parseInt(tierStr.substring(2));
        } catch (NumberFormatException e) {
            return null;
        }
        return new Ranking(tier, pos, null, null, 0, false);
    }

    /**
     * Convert TRTier kitTiers map (e.g. {"nethpot":"HT1","sword":"LT2"})
     * to the internal rankings map format.
     */
    private static Map<String, Ranking> convertKitTiers(JsonObject kitTiers) {
        Map<String, Ranking> rankings = new LinkedHashMap<>();
        if (kitTiers == null) return rankings;
        for (var entry : kitTiers.entrySet()) {
            String kit = entry.getKey();
            String tierStr = entry.getValue().getAsString();
            Ranking ranking = parseTierString(tierStr);
            if (ranking != null) {
                rankings.put(kit, ranking);
            }
        }
        return rankings;
    }

    /**
     * Fetch full player info from TRTier API by player name.
     * Endpoint: /player-details/{name}
     */
    public static CompletableFuture<PlayerInfo> get(HttpClient client, UUID uuid) {
        // TRTier API doesn't support UUID lookup, so this will use cached name if available
        // For UUID-based lookups, we return empty. The name-based search is preferred.
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/player-details/" + uuid;
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(PlayerInfo::parseFromTRTier)
                .whenComplete((i, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error getting player info ({})", uuid, t);
                });
    }

    /**
     * Get player rankings from TRTier API.
     * TRTier uses /player-details/{name} which returns kitTiers, so we use the overall-ranking
     * cache or player-details endpoint for name-based lookups.
     */
    public static CompletableFuture<Map<String, Ranking>> getRankings(HttpClient client, UUID uuid) {
        // TRTier doesn't have a UUID-based ranking endpoint.
        // We fall back to returning empty for UUID lookups.
        // Name-based searches will populate the cache via searchPlayer.
        return CompletableFuture.completedFuture(new LinkedHashMap<>());
    }

    /**
     * Search player by name using TRTier API.
     * Endpoint: /player-details/{name}
     */
    public static CompletableFuture<PlayerInfo> search(HttpClient client, String query) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/player-details/" + query;
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(PlayerInfo::parseFromTRTier)
                .whenComplete((i, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error searching player {}", query, t);
                });
    }

    /**
     * Parse TRTier API response into PlayerInfo.
     * TRTier response format:
     * {
     *   "minecraft_name": "Xpera",
     *   "region": "TR",
     *   "title": {"name": "Şampiyon", "points": 280},
     *   "points": 280,
     *   "overall_rank": 1,
     *   "kitTiers": {"nethpot": "HT1", "sword": "HT2", ...}
     * }
     */
    private static PlayerInfo parseFromTRTier(String json) {
        JsonObject obj = TierTagger.GSON.fromJson(json, JsonObject.class);
        if (obj == null) throw new RuntimeException("Invalid response from TRTier API");

        String name = obj.has("minecraft_name") ? obj.get("minecraft_name").getAsString() : "Unknown";
        String region = obj.has("region") ? obj.get("region").getAsString() : "TR";
        int points = obj.has("points") ? obj.get("points").getAsInt() : 0;
        int overallRank = obj.has("overall_rank") ? obj.get("overall_rank").getAsInt() : 0;

        // Parse kitTiers
        Map<String, Ranking> rankings = new LinkedHashMap<>();
        if (obj.has("kitTiers") && obj.get("kitTiers").isJsonObject()) {
            rankings = convertKitTiers(obj.getAsJsonObject("kitTiers"));
        }

        // Generate a fake UUID from the name (since TRTier doesn't return UUIDs)
        String fakeUuid = UUID.nameUUIDFromBytes(name.getBytes()).toString().replace("-", "");

        return new PlayerInfo(fakeUuid, name, rankings, region, points, overallRank,
                Collections.emptyList(), false);
    }

    public int getRegionColor() {
        return REGION_COLORS.getOrDefault(this.region.toUpperCase(Locale.ROOT), 0xffffff);
    }

    public static Optional<NamedRanking> getHighestRanking(Map<String, Ranking> rankings) {
        return rankings.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .min(Comparator.comparingInt(e -> e.getValue().comparableTier()))
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())));
    }

    @Getter
    @AllArgsConstructor
    public enum PointInfo {
        SAMPIYON("Şampiyon", 0xE6C622, 0xFDE047),
        SAVAS_HUKUMDARI("Savaş Hükümdarı", 0xFBB03B, 0xFFD13A),
        SAVAS_USTASI("Savaş Ustası", 0xCD285C, 0xD65474),
        SAVAS_SOVALYESI("Savaş Şovalyesi", 0xAD78D8, 0xC7A3E8),
        ELIT_DOVUSCU("Elit Dövüşçü", 0x9291D9, 0xADACE2),
        USTA_SAVASCI("Usta Savaşçı", 0x9291D9, 0xFFFFFF),
        TECR_DOVUSCU("Tecrübeli Dövüşçü", 0x6C7178, 0x8B979C),
        CIRAK_DOVUSCU("Çırak Dövüşçü", 0x6C7178, 0x8B979C),
        ACEMI_DOVUSCU("Acemi Dövüşçü", 0x6C7178, 0x8B979C),
        UNRANKED("Unranked", 0xFFFFFF, 0xFFFFFF);

        private final String title;
        private final int color;
        private final int accentColor;
    }

    public PointInfo getPointInfo() {
        if (this.points >= 250) {
            return PointInfo.SAMPIYON;
        } else if (this.points >= 100) {
            return PointInfo.SAVAS_HUKUMDARI;
        } else if (this.points >= 60) {
            return PointInfo.SAVAS_USTASI;
        } else if (this.points >= 40) {
            return PointInfo.SAVAS_SOVALYESI;
        } else if (this.points >= 30) {
            return PointInfo.ELIT_DOVUSCU;
        } else if (this.points >= 20) {
            return PointInfo.USTA_SAVASCI;
        } else if (this.points >= 10) {
            return PointInfo.TECR_DOVUSCU;
        } else if (this.points >= 5) {
            return PointInfo.CIRAK_DOVUSCU;
        } else if (this.points >= 1) {
            return PointInfo.ACEMI_DOVUSCU;
        } else {
            return PointInfo.UNRANKED;
        }
    }

    public List<NamedRanking> getSortedTiers() {
        List<NamedRanking> tiers = new ArrayList<>(this.rankings.entrySet().stream()
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())))
                .toList());

        tiers.sort(Comparator.comparing((NamedRanking a) -> a.ranking.retired, Boolean::compare)
                .thenComparingInt(a -> a.ranking.tier)
                .thenComparingInt(a -> a.ranking.pos));

        return tiers;
    }
}