package io.dmitriy.yasmcp.client;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

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
@ConcurrencyLimit(limitString = "${steam.api.max-concurrent-requests:4}")
public class SteamApiClientImpl implements SteamApiClient {

	static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(10);

	private static final Duration BASE_BACKOFF = Duration.ofMillis(200);

	private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

	private static final Logger log = LoggerFactory.getLogger(SteamApiClientImpl.class);

	private final SteamProperties.Api api;

	private final boolean includePlayedFreeGames;

	private final SteamWebApi webApi;

	private final JsonMapper jsonMapper;

	private final Sleeper sleeper;

	private final Clock clock;

	public SteamApiClientImpl(SteamProperties properties, SteamWebApi webApi, JsonMapper jsonMapper, Sleeper sleeper,
			Clock clock) {
		this.api = properties.api();
		this.includePlayedFreeGames = properties.library().includePlayedFreeGames();
		this.webApi = webApi;
		this.jsonMapper = jsonMapper;
		this.sleeper = sleeper;
		this.clock = clock;
	}

	@Override
	public OwnedGamesResponse getOwnedGames() {
		String key = api.requireKey();
		String steamId = api.requireSteamId();
		return get("GetOwnedGames", () -> webApi.getOwnedGames(key, steamId, true, includePlayedFreeGames),
				OwnedGamesResponse.class, OwnedGamesResponse::response);
	}

	@Override
	public RecentlyPlayedResponse getRecentlyPlayedGames(int count) {
		String key = api.requireKey();
		String steamId = api.requireSteamId();
		return get("GetRecentlyPlayedGames", () -> webApi.getRecentlyPlayedGames(key, steamId, count),
				RecentlyPlayedResponse.class, RecentlyPlayedResponse::response);
	}

	@Override
	public PlayerAchievementsResponse getPlayerAchievements(long appid, String language) {
		String key = api.requireKey();
		String steamId = api.requireSteamId();
		return get("GetPlayerAchievements", () -> webApi.getPlayerAchievements(key, steamId, appid, language),
				PlayerAchievementsResponse.class, PlayerAchievementsResponse::playerstats);
	}

	@Override
	public GameSchemaResponse getSchemaForGame(long appid, String language) {
		String key = api.requireKey();
		return get("GetSchemaForGame", () -> webApi.getSchemaForGame(key, appid, language), GameSchemaResponse.class,
				GameSchemaResponse::game);
	}

	@Override
	public NewsResponse getNewsForApp(long appid, int count, @Nullable Integer maxLength) {
		return get("GetNewsForApp", () -> webApi.getNewsForApp(appid, count, maxLength), NewsResponse.class,
				NewsResponse::appnews);
	}

	private <T> T get(String endpoint, Supplier<ResponseEntity<byte[]>> call, Class<T> type,
			Function<T, @Nullable Object> envelope) {
		long deadlineNanos = System.nanoTime() + api.totalTimeout().toNanos();
		for (int attempt = 0;; attempt++) {
			Outcome outcome = attempt(endpoint, call);
			if (outcome.status() >= 200 && outcome.status() < 300 || outcome.status() == 400) {
				return parse(outcome.body(), type, envelope);
			}
			ErrorCode code = classify(outcome.status());
			boolean retryable = code == ErrorCode.UPSTREAM_RATE_LIMITED || code == ErrorCode.UPSTREAM_UNAVAILABLE;
			Duration retryAfter = retryAfter(outcome.retryAfterHeader());
			if (!retryable || attempt >= api.retries() || (retryAfter != null && retryAfter.compareTo(MAX_RETRY_AFTER) > 0)) {
				throw failure(code);
			}
			Duration delay = nextDelay(attempt, retryAfter);
			if (System.nanoTime() + delay.toNanos() >= deadlineNanos) {
				log.warn("Steam {} giving up: total timeout would be exceeded", endpoint);
				throw failure(code);
			}
			pause(endpoint, attempt, outcome.status(), delay);
		}
	}

	/** One HTTP round trip. Never throws for upstream status; I/O failure becomes status {@code -1}. */
	private Outcome attempt(String endpoint, Supplier<ResponseEntity<byte[]>> call) {
		try {
			ResponseEntity<byte[]> response = call.get();
			return new Outcome(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"),
					bounded(response.getBody()));
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

	private static byte @Nullable [] bounded(byte @Nullable [] body) {
		return body != null && body.length > MAX_BODY_BYTES ? null : body;
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

	private static Duration nextDelay(int attempt, @Nullable Duration retryAfter) {
		if (retryAfter != null) {
			return retryAfter;
		}
		return BASE_BACKOFF.multipliedBy(1L << attempt).multipliedBy(500 + ThreadLocalRandom.current().nextInt(501)).dividedBy(1000);
	}

	private void pause(String endpoint, int attempt, int status, Duration delay) {
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
