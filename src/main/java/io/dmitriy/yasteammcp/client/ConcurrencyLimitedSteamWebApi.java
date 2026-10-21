package io.dmitriy.yasteammcp.client;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.annotation.ConcurrencyLimit;

/**
 * Caps in-flight Steam requests, one shared limit for all five calls. It sits <em>below</em>
 * {@code @Retryable} on {@link SteamApiClientImpl} on purpose: advice order between the two
 * annotations on one bean is not controllable, and the limit must wrap a single attempt so a
 * retry sleep never holds a slot.
 */
@ConcurrencyLimit(limitString = "${steam.api.max-concurrent-requests:4}")
public class ConcurrencyLimitedSteamWebApi implements SteamWebApi {

	private final SteamWebApi delegate;

	public ConcurrencyLimitedSteamWebApi(SteamWebApi delegate) {
		this.delegate = delegate;
	}

	@Override
	public ResponseEntity<byte[]> getOwnedGames(String key, String steamId, boolean includeAppInfo,
			boolean includePlayedFreeGames) {
		return delegate.getOwnedGames(key, steamId, includeAppInfo, includePlayedFreeGames);
	}

	@Override
	public ResponseEntity<byte[]> getRecentlyPlayedGames(String key, String steamId, int count) {
		return delegate.getRecentlyPlayedGames(key, steamId, count);
	}

	@Override
	public ResponseEntity<byte[]> getPlayerAchievements(String key, String steamId, long appid, String language) {
		return delegate.getPlayerAchievements(key, steamId, appid, language);
	}

	@Override
	public ResponseEntity<byte[]> getSchemaForGame(String key, long appid, String language) {
		return delegate.getSchemaForGame(key, appid, language);
	}

	@Override
	public ResponseEntity<byte[]> getNewsForApp(long appid, int count, @Nullable Integer maxLength) {
		return delegate.getNewsForApp(appid, count, maxLength);
	}

}
