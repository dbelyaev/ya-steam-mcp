package io.dmitriy.yasmcp.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.support.SteamIdValidator;

/**
 * Binding fails fast only for settings that are unsafe (base URL, timeouts, retries).
 * A missing or malformed key / SteamID must not stop startup: tools report it at call time.
 */
@ConfigurationProperties("steam")
public record SteamProperties(@DefaultValue Api api, @DefaultValue Library library, @DefaultValue Store store) {

	public record Api(
			@DefaultValue("https://api.steampowered.com") URI baseUrl,
			@DefaultValue("") String key,
			@DefaultValue("") String steamId,
			@DefaultValue("3s") Duration connectTimeout,
			@DefaultValue("10s") Duration readTimeout,
			@DefaultValue("2") int retries,
			@DefaultValue("english") String defaultLanguage,
			@DefaultValue("false") boolean allowInsecureTestBaseUrl) {

		private static final String OFFICIAL_HOST = "api.steampowered.com";

		private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

		public Api {
			validateBaseUrl(baseUrl, allowInsecureTestBaseUrl);
			requireRange("steam.api.connect-timeout", connectTimeout, Duration.ofMillis(100), Duration.ofSeconds(30));
			requireRange("steam.api.read-timeout", readTimeout, Duration.ofMillis(100), Duration.ofSeconds(60));
			if (retries < 0 || retries > 5) {
				throw new IllegalArgumentException("steam.api.retries must be between 0 and 5");
			}
		}

		public String requireKey() {
			if (key == null || key.isBlank()) {
				throw new SteamException(ErrorCode.CONFIGURATION_MISSING,
						"STEAM_API_KEY is not configured; set it in the MCP server environment");
			}
			return key;
		}

		public String requireSteamId() {
			if (steamId == null || steamId.isBlank()) {
				throw new SteamException(ErrorCode.CONFIGURATION_MISSING,
						"STEAM_ID is not configured; set a 17-digit SteamID64 in the MCP server environment");
			}
			if (!SteamIdValidator.isValid(steamId)) {
				throw new SteamException(ErrorCode.CONFIGURATION_MISSING,
						"STEAM_ID is invalid; it must be a 17-digit decimal SteamID64");
			}
			return steamId;
		}

		private static void validateBaseUrl(URI uri, boolean allowInsecure) {
			if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
					|| uri.getFragment() != null || !isRootPath(uri.getPath())) {
				throw new IllegalArgumentException("steam.api.base-url must be a bare origin without credentials, path or query");
			}
			String host = uri.getHost().toLowerCase();
			boolean official = "https".equals(uri.getScheme()) && OFFICIAL_HOST.equals(host)
					&& (uri.getPort() == -1 || uri.getPort() == 443);
			boolean testOverride = allowInsecure && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
					&& LOOPBACK_HOSTS.contains(host);
			if (!official && !testOverride) {
				throw new IllegalArgumentException("steam.api.base-url must be https://" + OFFICIAL_HOST);
			}
		}

		private static boolean isRootPath(String path) {
			return path == null || path.isEmpty() || "/".equals(path);
		}

		private static void requireRange(String name, Duration value, Duration min, Duration max) {
			if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
				throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
			}
		}

	}

	public record Library(@DefaultValue("true") boolean includePlayedFreeGames) {
	}

	public record Store(@DefaultValue("false") boolean enrichmentEnabled) {
	}

}
