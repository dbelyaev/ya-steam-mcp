package io.dmitriy.yasmcp;

import java.net.URI;
import java.time.Duration;

import io.dmitriy.yasmcp.config.SteamProperties;

/** Builds {@link SteamProperties} for unit tests that do not start Spring. */
public final class TestProperties {

	public static final String STEAM_ID = "76561197960287930";

	private TestProperties() {
	}

	public static SteamProperties withCredentials() {
		return of("test-key", STEAM_ID);
	}

	public static SteamProperties of(String key, String steamId) {
		var api = new SteamProperties.Api(URI.create("https://api.steampowered.com"), key, steamId,
				Duration.ofSeconds(3), Duration.ofSeconds(10), 2, Duration.ofMillis(200), Duration.ofSeconds(30), 4,
				"english", false);
		return new SteamProperties(api, new SteamProperties.Library(true), new SteamProperties.Store(false));
	}

}
