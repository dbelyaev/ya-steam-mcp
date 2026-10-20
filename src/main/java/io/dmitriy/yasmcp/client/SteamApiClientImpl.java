package io.dmitriy.yasmcp.client;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.resilience.annotation.Retryable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import io.dmitriy.yasmcp.config.SteamProperties;
import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.model.TransientUpstreamException;
import io.dmitriy.yasmcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasmcp.steamdto.NewsResponse;
import io.dmitriy.yasmcp.steamdto.OwnedGamesResponse;
import io.dmitriy.yasmcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasmcp.steamdto.RecentlyPlayedResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API key travels in the query string, so nothing here may log or wrap a URI:
 * statuses are mapped without exceptions that embed bodies, I/O failures are translated
 * immediately, and messages are fixed strings. Retries (I/O, 5xx only) come from the
 * class-level {@code @Retryable}, so callers must use the Spring-managed proxy.
 */
@Retryable(includes = TransientUpstreamException.class, maxRetriesString = "${steam.api.retries:2}",
		delayString = "${steam.api.retry-delay:200ms}", multiplier = 2.0, jitter = 50, maxDelay = 2000,
		timeoutString = "${steam.api.total-timeout:30s}")
public class SteamApiClientImpl implements SteamApiClient {

	private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

	private static final Logger log = LoggerFactory.getLogger(SteamApiClientImpl.class);

	private final SteamProperties.Api api;

	private final boolean includePlayedFreeGames;

	private final SteamWebApi webApi;

	private final JsonMapper jsonMapper;

	public SteamApiClientImpl(SteamProperties properties, SteamWebApi webApi, JsonMapper jsonMapper) {
		this.api = properties.api();
		this.includePlayedFreeGames = properties.library().includePlayedFreeGames();
		this.webApi = webApi;
		this.jsonMapper = jsonMapper;
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
		Outcome outcome = attempt(endpoint, call);
		int status = outcome.status();
		if (status >= 200 && status < 300 || status == 400) {
			return parse(outcome.body(), type, envelope);
		}
		log.warn("Steam {} failed with status {}", endpoint, status);
		throw failure(status);
	}

	/** One HTTP round trip. Never throws for upstream status; I/O failure becomes status {@code -1}. */
	private Outcome attempt(String endpoint, Supplier<ResponseEntity<byte[]>> call) {
		try {
			ResponseEntity<byte[]> response = call.get();
			return new Outcome(response.getStatusCode().value(), bounded(response.getBody()));
		}
		catch (ResourceAccessException ex) {
			// message contains the request URI incl. key: log only the exception class
			log.warn("Steam {} I/O failure: {}", endpoint, ex.getCause() != null ? ex.getCause().getClass().getName() : "unknown");
			return new Outcome(-1, null);
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
		catch (JacksonException _) {
			// Jackson messages quote payload fragments: do not propagate
			throw failure(ErrorCode.UPSTREAM_BAD_RESPONSE);
		}
		if (parsed == null || envelope.apply(parsed) == null) {
			throw failure(ErrorCode.UPSTREAM_BAD_RESPONSE);
		}
		return parsed;
	}

	private static SteamException failure(int status) {
		if (status == -1 || status >= 500) {
			return new TransientUpstreamException();
		}
		return switch (status) {
			case 401, 403 -> failure(ErrorCode.UPSTREAM_AUTH_FAILED);
			case 429 -> failure(ErrorCode.UPSTREAM_RATE_LIMITED);
			default -> failure(ErrorCode.UPSTREAM_BAD_RESPONSE);
		};
	}

	private static SteamException failure(ErrorCode code) {
		return new SteamException(code, switch (code) {
			case UPSTREAM_AUTH_FAILED -> "Steam rejected the API key";
			case UPSTREAM_RATE_LIMITED -> "Steam API rate limit reached";
			case UPSTREAM_UNAVAILABLE -> "Steam API is temporarily unavailable";
			default -> "Steam API returned an unexpected response";
		});
	}

	/**
	 * One round trip. A record holding an array, so {@code equals}/{@code hashCode} compare the content
	 * (Sonar S6218), while {@code toString} deliberately reports only the body length: the body is untrusted
	 * upstream content that must never reach a log. {@code Retry-After} is not kept: 429 is not retried (D11).
	 * Package-private so the guarantees can be tested.
	 */
	record Outcome(int status, byte @Nullable [] body) {

		@Override
		public boolean equals(@Nullable Object other) {
			return other instanceof Outcome that && status == that.status && Arrays.equals(body, that.body);
		}

		@Override
		public int hashCode() {
			return 31 * Integer.hashCode(status) + Arrays.hashCode(body);
		}

		@Override
		public String toString() {
			return "Outcome[status=" + status + ", body=" + (body == null ? "none" : body.length + " bytes") + "]";
		}

	}

}
