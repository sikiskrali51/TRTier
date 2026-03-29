package com.kevin.tiertagger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerInfo;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class TierCache {
    private static final List<GameMode> GAMEMODES = new ArrayList<>();
    private static final Map<UUID, Optional<Map<String, PlayerInfo.Ranking>>> TIERS = new ConcurrentHashMap<>();
    // Name -> UUID mapping for TRTier (which uses names instead of UUIDs)
    private static final Map<String, UUID> NAME_TO_UUID = new ConcurrentHashMap<>();
    // Name -> rankings cache for quick lookups by name
    private static final Map<String, Map<String, PlayerInfo.Ranking>> NAME_RANKINGS = new ConcurrentHashMap<>();

    public static void init() {
        try {
            GAMEMODES.clear();
            GAMEMODES.addAll(GameMode.fetchGamemodes(TierTagger.getClient()).get());
            TierTagger.getLogger().info("Found {} kits: {}", GAMEMODES.size(), GAMEMODES.stream().map(GameMode::id).toList());
        } catch (ExecutionException e) {
            TierTagger.getLogger().error("Failed to load gamemodes!", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Pre-load overall ranking from TRTier to populate caches
        preloadOverallRanking();
    }

    /**
     * Pre-load the overall ranking from TRTier API to populate name-based caches.
     * This allows UUID-based lookups to work by matching player names from the world.
     */
    private static void preloadOverallRanking() {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/overall-ranking";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        TierTagger.getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    try {
                        JsonArray array = TierTagger.GSON.fromJson(response.body(), JsonArray.class);
                        if (array == null) return;

                        int count = 0;
                        for (JsonElement elem : array) {
                            JsonObject obj = elem.getAsJsonObject();
                            String name = obj.has("name") ? obj.get("name").getAsString() : null;
                            if (name == null) continue;

                            JsonObject kitTiers = obj.has("kitTiers") ? obj.getAsJsonObject("kitTiers") : null;
                            if (kitTiers == null) continue;

                            Map<String, PlayerInfo.Ranking> rankings = new LinkedHashMap<>();
                            for (var entry : kitTiers.entrySet()) {
                                String kit = entry.getKey();
                                String tierStr = entry.getValue().getAsString();
                                PlayerInfo.Ranking ranking = parseTierString(tierStr);
                                if (ranking != null) {
                                    rankings.put(kit, ranking);
                                }
                            }

                            NAME_RANKINGS.put(name.toLowerCase(Locale.ROOT), rankings);
                            count++;
                        }

                        TierTagger.getLogger().info("Pre-loaded {} players from TRTier overall ranking", count);
                    } catch (Exception e) {
                        TierTagger.getLogger().warn("Error parsing overall ranking", e);
                    }
                })
                .exceptionally(t -> {
                    TierTagger.getLogger().warn("Error fetching overall ranking from TRTier", t);
                    return null;
                });
    }

    private static PlayerInfo.Ranking parseTierString(String tierStr) {
        if (tierStr == null || tierStr.length() < 3) return null;
        int pos = tierStr.charAt(0) == 'H' ? 0 : 1;
        int tier;
        try {
            tier = Integer.parseInt(tierStr.substring(2));
        } catch (NumberFormatException e) {
            return null;
        }
        return new PlayerInfo.Ranking(tier, pos, null, null, 0, false);
    }

    public static List<GameMode> getGamemodes() {
        if (GAMEMODES.isEmpty()) {
            return Collections.singletonList(GameMode.NONE);
        } else {
            return GAMEMODES;
        }
    }

    public static Optional<Map<String, PlayerInfo.Ranking>> getPlayerRankings(UUID uuid) {
        return TIERS.computeIfAbsent(uuid, u -> {
            // TRTier doesn't support UUID lookups, but we may have name data from preloaded rankings
            return Optional.empty();
        });
    }

    /**
     * Look up rankings by player name (used for TRTier name-based API).
     * Also populates the UUID cache for future lookups.
     */
    public static Optional<Map<String, PlayerInfo.Ranking>> getPlayerRankingsByName(String name, UUID uuid) {
        // First check if we already have UUID-based cache
        Optional<Map<String, PlayerInfo.Ranking>> cached = TIERS.get(uuid);
        if (cached != null && cached.isPresent()) {
            return cached;
        }

        // Check name-based cache from preloaded data
        Map<String, PlayerInfo.Ranking> nameRankings = NAME_RANKINGS.get(name.toLowerCase(Locale.ROOT));
        if (nameRankings != null && !nameRankings.isEmpty()) {
            Optional<Map<String, PlayerInfo.Ranking>> result = Optional.of(nameRankings);
            TIERS.put(uuid, result);
            return result;
        }

        // If not found, trigger an async fetch by name
        TIERS.computeIfAbsent(uuid, u -> {
            fetchPlayerByName(name, uuid);
            return Optional.empty();
        });

        return Optional.empty();
    }

    /**
     * Fetch player details from TRTier by name and populate caches.
     */
    private static void fetchPlayerByName(String name, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/player-details/" + name;
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        TierTagger.getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    try {
                        JsonObject obj = TierTagger.GSON.fromJson(response.body(), JsonObject.class);
                        if (obj == null || !obj.has("kitTiers")) return;

                        JsonObject kitTiers = obj.getAsJsonObject("kitTiers");
                        Map<String, PlayerInfo.Ranking> rankings = new LinkedHashMap<>();
                        for (var entry : kitTiers.entrySet()) {
                            String kit = entry.getKey();
                            String tierStr = entry.getValue().getAsString();
                            PlayerInfo.Ranking ranking = parseTierString(tierStr);
                            if (ranking != null) {
                                rankings.put(kit, ranking);
                            }
                        }

                        TIERS.put(uuid, Optional.of(rankings));
                        NAME_RANKINGS.put(name.toLowerCase(Locale.ROOT), rankings);
                    } catch (Exception e) {
                        TierTagger.getLogger().warn("Error fetching player details for {}", name, e);
                    }
                })
                .exceptionally(t -> {
                    TierTagger.getLogger().warn("Error fetching player from TRTier: {}", name, t);
                    return null;
                });
    }

    public static CompletableFuture<PlayerInfo> searchPlayer(String query) {
        return PlayerInfo.search(TierTagger.getClient(), query).thenApply(p -> {
            UUID uuid = parseUUID(p.uuid());
            TIERS.put(uuid, Optional.of(p.rankings()));
            NAME_RANKINGS.put(p.name().toLowerCase(Locale.ROOT), p.rankings());
            return p;
        });
    }

    public static void clearCache() {
        TIERS.clear();
        NAME_RANKINGS.clear();
        NAME_TO_UUID.clear();
    }

    public static GameMode findNextMode(GameMode current) {
        if (GAMEMODES.isEmpty()) {
            return GameMode.NONE;
        } else {
            int idx = GAMEMODES.indexOf(current);
            return GAMEMODES.get((idx + 1) % GAMEMODES.size());
        }
    }

    public static Optional<GameMode> findMode(String id) {
        return GAMEMODES.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst();
    }

    public static GameMode findModeOrUgly(String id) {
        return findMode(id).orElseGet(() -> new GameMode(id, id));
    }

    private static UUID parseUUID(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (Exception e) {
            try {
                long mostSignificant = Long.parseUnsignedLong(uuid.substring(0, 16), 16);
                long leastSignificant = Long.parseUnsignedLong(uuid.substring(16), 16);
                return new UUID(mostSignificant, leastSignificant);
            } catch (Exception ex) {
                return UUID.nameUUIDFromBytes(uuid.getBytes());
            }
        }
    }

    private TierCache() {
    }
}
