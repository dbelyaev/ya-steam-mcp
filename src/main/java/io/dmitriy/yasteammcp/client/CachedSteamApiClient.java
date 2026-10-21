package io.dmitriy.yasteammcp.client;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import io.dmitriy.yasteammcp.config.SteamProperties;
import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.steamdto.NewsResponse;
import io.dmitriy.yasteammcp.steamdto.OwnedGamesResponse;
import io.dmitriy.yasteammcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasteammcp.steamdto.RecentlyPlayedResponse;

/**
 * Short in-memory cache in front of the Steam client, one cache and TTL per endpoint.
 * <ul>
 * <li>Only successful, <em>complete-looking</em> responses are stored: errors are never cached,
 * and neither are answers that look like missing data (no owned games, {@code success:false},
 * empty achievements, empty schema), so a privacy change or a transient blank cannot be pinned.</li>
 * <li>Keys hold SteamID, AppID, language and counts; never the API key.</li>
 * <li>Hits and misses are reported to {@link CacheTrace} so tool responses can set {@code meta.cached}.</li>
 * </ul>
 * It wraps the retrying client, so a hit costs no HTTP slot and no retry.
 */
public class CachedSteamApiClient implements SteamApiClient {

	private static final Logger log = LoggerFactory.getLogger(CachedSteamApiClient.class);

	private final SteamApiClient delegate;

	private final boolean enabled;

	private final String steamId;

	private final boolean includePlayedFreeGames;

	private final Cache<String, OwnedGamesResponse> ownedGames;

	private final Cache<String, RecentlyPlayedResponse> recentGames;

	private final Cache<String, PlayerAchievementsResponse> achievements;

	private final Cache<String, GameSchemaResponse> schemas;

	private final Cache<String, NewsResponse> news;

	public CachedSteamApiClient(SteamApiClient delegate, SteamProperties properties, Ticker ticker) {
		this.delegate = delegate;
		var cache = properties.cache();
		this.enabled = cache.enabled();
		this.steamId = properties.api().steamId();
		this.includePlayedFreeGames = properties.library().includePlayedFreeGames();
		this.ownedGames = build(cache.ownedGamesTtl(), 4, ticker);
		this.recentGames = build(cache.recentGamesTtl(), 128, ticker);
		this.achievements = build(cache.achievementsTtl(), 512, ticker);
		this.schemas = build(cache.schemaTtl(), 512, ticker);
		this.news = build(cache.newsTtl(), 512, ticker);
	}

	private static <V> Cache<String, V> build(Duration ttl, long maxSize, Ticker ticker) {
		return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).ticker(ticker).build();
	}

	@Override
	public OwnedGamesResponse getOwnedGames() {
		String key = steamId + "|free=" + includePlayedFreeGames;
		return cached("GetOwnedGames", ownedGames, key, delegate::getOwnedGames, CachedSteamApiClient::hasOwnedGames);
	}

	@Override
	public RecentlyPlayedResponse getRecentlyPlayedGames(int count) {
		return cached("GetRecentlyPlayedGames", recentGames, steamId + "|count=" + count,
				() -> delegate.getRecentlyPlayedGames(count), r -> r.response() != null);
	}

	@Override
	public PlayerAchievementsResponse getPlayerAchievements(long appid, String language) {
		return cached("GetPlayerAchievements", achievements, steamId + "|" + appid + "|" + language,
				() -> delegate.getPlayerAchievements(appid, language), CachedSteamApiClient::hasAchievements);
	}

	@Override
	public GameSchemaResponse getSchemaForGame(long appid, String language) {
		return cached("GetSchemaForGame", schemas, appid + "|" + language, () -> delegate.getSchemaForGame(appid, language),
				CachedSteamApiClient::hasSchema);
	}

	@Override
	public NewsResponse getNewsForApp(long appid, int count, @Nullable Integer maxLength) {
		return cached("GetNewsForApp", news, appid + "|" + count + "|" + maxLength,
				() -> delegate.getNewsForApp(appid, count, maxLength), n -> n.appnews() != null);
	}

	private <V> V cached(String endpoint, Cache<String, V> cache, String key, Supplier<V> loader,
			Predicate<V> worthCaching) {
		if (!enabled) {
			return loader.get();
		}
		V hit = cache.getIfPresent(key);
		if (hit != null) {
			log.debug("Steam {} served from cache", endpoint);
			CacheTrace.hit();
			return hit;
		}
		V loaded = loader.get();
		if (worthCaching.test(loaded)) {
			cache.put(key, loaded);
		}
		CacheTrace.miss();
		return loaded;
	}

	private static boolean hasOwnedGames(OwnedGamesResponse r) {
		OwnedGamesResponse.Body body = r.response();
		List<OwnedGamesResponse.Game> games = body == null ? null : body.games();
		return games != null && !games.isEmpty();
	}

	private static boolean hasAchievements(PlayerAchievementsResponse r) {
		PlayerAchievementsResponse.Stats stats = r.playerstats();
		return stats != null && !Boolean.FALSE.equals(stats.success()) && stats.achievements() != null
				&& !stats.achievements().isEmpty();
	}

	private static boolean hasSchema(GameSchemaResponse r) {
		GameSchemaResponse.Game game = r.game();
		return game != null && (game.gameName() != null || game.availableGameStats() != null);
	}

}
