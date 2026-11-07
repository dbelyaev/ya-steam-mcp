package io.dmitriy.yasteammcp;

import java.net.URI;
import java.time.Duration;

import io.dmitriy.yasteammcp.config.SteamProperties;

/** Builds {@link SteamProperties} for unit tests that do not start Spring. */
public final class TestProperties {

	public static final String STEAM_ID = "76561197960287930";

	private TestProperties() {
	}

	public static SteamProperties withCredentials() {
		return of("test-key", STEAM_ID);
	}

	/** Api settings for tests that talk to a local fake: insecure base URL allowed, one second connect timeout. */
	public static SteamProperties.Api api(URI baseUrl, String key, Duration readTimeout, int retries,
			Duration retryDelay, Duration totalTimeout, int maxConcurrentRequests) {
		return new SteamProperties.Api(baseUrl, key, STEAM_ID, Duration.ofSeconds(1), readTimeout, retries,
				retryDelay, totalTimeout, maxConcurrentRequests, "english", true);
	}

	/** Wraps the given api settings with the defaults for everything else. */
	public static SteamProperties of(SteamProperties.Api api) {
		var cache = new SteamProperties.Cache(true, Duration.ofMinutes(5), Duration.ofMinutes(2),
				Duration.ofMinutes(10), Duration.ofHours(24), Duration.ofMinutes(10));
		return new SteamProperties(api, new SteamProperties.Library(true), new SteamProperties.Store(false), cache);
	}

	public static SteamProperties of(String key, String steamId) {
		var api = new SteamProperties.Api(URI.create("https://api.steampowered.com"), key, steamId,
				Duration.ofSeconds(3), Duration.ofSeconds(10), 2, Duration.ofMillis(200), Duration.ofSeconds(30), 4,
				"english", false);
		return of(api);
	}

	public static SteamProperties withCacheDisabled(SteamProperties base) {
		var c = base.cache();
		var cache = new SteamProperties.Cache(false, c.ownedGamesTtl(), c.recentGamesTtl(), c.achievementsTtl(),
				c.schemaTtl(), c.newsTtl());
		return new SteamProperties(base.api(), base.library(), base.store(), cache);
	}

}
