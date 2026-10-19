package io.dmitriy.yasmcp.client;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import io.dmitriy.yasmcp.config.SteamProperties;
import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasmcp.steamdto.NewsResponse;
import io.dmitriy.yasmcp.steamdto.OwnedGamesResponse;
import io.dmitriy.yasmcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasmcp.steamdto.RecentlyPlayedResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API key travels in the query string, so nothing here may log or wrap a URI:
 * responses are read through {@code exchange} (no status exceptions with bodies),
 * I/O failures are translated immediately, and messages are fixed strings.
 */
public class SteamApiClientImpl implements SteamApiClient {

	static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(10);

	private static final Duration BASE_BACKOFF = Duration.ofMillis(200);

	private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

	private static final Logger log = LoggerFactory.getLogger(SteamApiClientImpl.class);

	private final SteamProperties.Api api;

	private final boolean includePlayedFreeGames;

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	private final Sleeper sleeper;

	private final Clock clock;

	public SteamApiClientImpl(SteamProperties properties, RestClient restClient, JsonMapper jsonMapper, Sleeper sleeper,
			Clock clock) {
		this.api = properties.api();
		this.includePlayedFreeGames = properties.library().includePlayedFreeGames();
		this.restClient = restClient;
		this.jsonMapper = jsonMapper;
		this.sleeper = sleeper;
		this.clock = clock;
	}

	@Override
	public OwnedGamesResponse getOwnedGames() {
		Map<String, Object> params = keyed();
		params.put("include_appinfo", true);
		params.put("include_played_free_games", includePlayedFreeGames);
		return get("GetOwnedGames", "/IPlayerService/GetOwnedGames/v1/", params, OwnedGamesResponse.class,
				OwnedGamesResponse::response);
	}

	@Override
	public RecentlyPlayedResponse getRecentlyPlayedGames(int count) {
		Map<String, Object> params = keyed();
		params.put("count", count);
		return get("GetRecentlyPlayedGames", "/IPlayerService/GetRecentlyPlayedGames/v1/", params,
				RecentlyPlayedResponse.class, RecentlyPlayedResponse::response);
	}

	@Override
	public PlayerAchievementsResponse getPlayerAchievements(long appid, String language) {
		Map<String, Object> params = keyed();
		params.put("appid", appid);
		params.put("l", language);
		return get("GetPlayerAchievements", "/ISteamUserStats/GetPlayerAchievements/v1/", params,
				PlayerAchievementsResponse.class, PlayerAchievementsResponse::playerstats);
	}

	@Override
	public GameSchemaResponse getSchemaForGame(long appid, String language) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("key", api.requireKey());
		params.put("appid", appid);
		params.put("l", language);
		return get("GetSchemaForGame", "/ISteamUserStats/GetSchemaForGame/v2/", params, GameSchemaResponse.class,
				GameSchemaResponse::game);
	}

	@Override
	public NewsResponse getNewsForApp(long appid, int count, @Nullable Integer maxLength) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("appid", appid);
		params.put("count", count);
		if (maxLength != null) {
			params.put("maxlength", maxLength);
		}
		return get("GetNewsForApp", "/ISteamNews/GetNewsForApp/v2/", params, NewsResponse.class,
				NewsResponse::appnews);
	}

	private Map<String, Object> keyed() {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("key", api.requireKey());
		params.put("steamid", api.requireSteamId());
		return params;
	}

	private <T> T get(String endpoint, String path, Map<String, Object> params, Class<T> type,
			Function<T, @Nullable Object> envelope) {
		for (int attempt = 0;; attempt++) {
			Outcome outcome = attempt(endpoint, path, params);
			if (outcome.status() >= 200 && outcome.status() < 300 || outcome.status() == 400) {
				return parse(outcome.body(), type, envelope);
			}
			ErrorCode code = classify(outcome.status());
			boolean retryable = code == ErrorCode.UPSTREAM_RATE_LIMITED || code == ErrorCode.UPSTREAM_UNAVAILABLE;
			Duration retryAfter = retryAfter(outcome.retryAfterHeader());
			if (!retryable || attempt >= api.retries() || (retryAfter != null && retryAfter.compareTo(MAX_RETRY_AFTER) > 0)) {
				throw failure(code);
			}
			pause(endpoint, attempt, outcome.status(), retryAfter);
		}
	}

	/** One HTTP round trip. Never throws for upstream status; I/O failure becomes status {@code -1}. */
	private Outcome attempt(String endpoint, String path, Map<String, Object> params) {
		try {
			return restClient.get().uri(uriBuilder -> {
				uriBuilder.path(path);
				params.keySet().forEach(name -> uriBuilder.queryParam(name, "{" + name + "}"));
				return uriBuilder.build(params);
			}).exchange((request, response) -> new Outcome(response.getStatusCode().value(),
					response.getHeaders().getFirst("Retry-After"), readBounded(response.getBody())));
		}
		catch (ResourceAccessException ex) {
			// message contains the request URI incl. key: log only the exception class
			log.warn("Steam {} I/O failure: {}", endpoint, ex.getCause() != null ? ex.getCause().getClass().getName() : "unknown");
			return new Outcome(-1, null, null);
		}
		catch (RuntimeException ex) {
			log.warn("Steam {} request failed: {}", endpoint, ex.getClass().getName());
			throw failure(ErrorCode.UPSTREAM_BAD_RESPONSE);
		}
	}

	private static byte @Nullable [] readBounded(java.io.InputStream in) throws IOException {
		byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
		return body.length > MAX_BODY_BYTES ? null : body;
	}

	private <T> T parse(byte @Nullable [] body, Class<T> type, Function<T, @Nullable Object> envelope) {
		if (body == null || body.length == 0) {
			throw failure(ErrorCode.UPSTREAM_BAD_RESPONSE);
		}
		T parsed;
		try {
			parsed = jsonMapper.readValue(body, type);
		}
		catch (JacksonException ex) {
			// Jackson messages quote payload fragments: do not propagate
			throw failure(ErrorCode.UPSTREAM_BAD_RESPONSE);
		}
		if (parsed == null || envelope.apply(parsed) == null) {
			throw failure(ErrorCode.UPSTREAM_BAD_RESPONSE);
		}
		return parsed;
	}

	private static ErrorCode classify(int status) {
		if (status == -1 || status >= 500) {
			return ErrorCode.UPSTREAM_UNAVAILABLE;
		}
		return switch (status) {
			case 401, 403 -> ErrorCode.UPSTREAM_AUTH_FAILED;
			case 429 -> ErrorCode.UPSTREAM_RATE_LIMITED;
			default -> ErrorCode.UPSTREAM_BAD_RESPONSE;
		};
	}

	private void pause(String endpoint, int attempt, int status, @Nullable Duration retryAfter) {
		Duration delay = retryAfter != null ? retryAfter
				: BASE_BACKOFF.multipliedBy(1L << attempt).multipliedBy(500 + ThreadLocalRandom.current().nextInt(501)).dividedBy(1000);
		log.warn("Steam {} attempt {} failed (status {}); retrying in {} ms", endpoint, attempt + 1, status, delay.toMillis());
		try {
			sleeper.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw failure(ErrorCode.UPSTREAM_UNAVAILABLE);
		}
	}

	/** Seconds or HTTP-date; {@code null} when absent or unparseable. */
	private @Nullable Duration retryAfter(@Nullable String header) {
		if (header == null || header.isBlank()) {
			return null;
		}
		String value = header.trim();
		try {
			return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
		}
		catch (NumberFormatException ignored) {
			// fall through to HTTP-date
		}
		try {
			Duration d = Duration.between(clock.instant(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
			return d.isNegative() ? Duration.ZERO : d;
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static SteamException failure(ErrorCode code) {
		return new SteamException(code, switch (code) {
			case UPSTREAM_AUTH_FAILED -> "Steam rejected the API key";
			case UPSTREAM_RATE_LIMITED -> "Steam API rate limit reached";
			case UPSTREAM_UNAVAILABLE -> "Steam API is temporarily unavailable";
			default -> "Steam API returned an unexpected response";
		});
	}

	private record Outcome(int status, @Nullable String retryAfterHeader, byte @Nullable [] body) {
	}

}
