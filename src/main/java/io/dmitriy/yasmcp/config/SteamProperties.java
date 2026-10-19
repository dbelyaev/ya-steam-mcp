package io.dmitriy.yasmcp.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.support.SteamIdValidator;

/**
 * Binding fails fast only for settings that are unsafe (base URL, timeouts, retries).
 * A missing or malformed key / SteamID must not stop startup: tools report it at call time.
 */
@Validated
@ConfigurationProperties("steam")
public record SteamProperties(@DefaultValue @Valid Api api, @DefaultValue Library library, @DefaultValue Store store) {

	public record Api(
			@DefaultValue("https://api.steampowered.com") @NotNull URI baseUrl,
			@DefaultValue("") String key,
			@DefaultValue("") String steamId,
			@DefaultValue("3s") @NotNull Duration connectTimeout,
			@DefaultValue("10s") @NotNull Duration readTimeout,
			@DefaultValue("2") @Min(0) @Max(5) int retries,
			@DefaultValue("30s") @NotNull Duration totalTimeout,
			@DefaultValue("4") @Min(1) @Max(16) int maxConcurrentRequests,
			@DefaultValue("english") @NotBlank String defaultLanguage,
			@DefaultValue("false") boolean allowInsecureTestBaseUrl) {

		private static final String OFFICIAL_HOST = "api.steampowered.com";

		private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

		public Api {
			// cross-field security invariant: enforced even if bean validation is bypassed
			validateBaseUrl(baseUrl, allowInsecureTestBaseUrl);
		}

		@AssertTrue(message = "steam.api.connect-timeout must be between 100ms and 30s")
		public boolean isConnectTimeoutInRange() {
			return inRange(connectTimeout, Duration.ofMillis(100), Duration.ofSeconds(30));
		}

		@AssertTrue(message = "steam.api.total-timeout must be between 1s and 2m")
		public boolean isTotalTimeoutInRange() {
			return inRange(totalTimeout, Duration.ofSeconds(1), Duration.ofMinutes(2));
		}

		@AssertTrue(message = "steam.api.read-timeout must be between 100ms and 60s")
		public boolean isReadTimeoutInRange() {
			return inRange(readTimeout, Duration.ofMillis(100), Duration.ofSeconds(60));
		}

		/** Generated record toString would print the key and SteamID; both must stay out of logs. */
		@Override
		public String toString() {
			return "Api[baseUrl=" + baseUrl + ", key=" + (key == null || key.isBlank() ? "<unset>" : "<redacted>")
					+ ", steamId=" + (steamId == null || steamId.isBlank() ? "<unset>" : "<redacted>")
					+ ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + ", totalTimeout=" + totalTimeout + ", maxConcurrentRequests=" + maxConcurrentRequests
					+ ", retries=" + retries
					+ ", defaultLanguage=" + defaultLanguage + ", allowInsecureTestBaseUrl=" + allowInsecureTestBaseUrl
					+ "]";
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

		private static boolean inRange(Duration value, Duration min, Duration max) {
			return value == null || (value.compareTo(min) >= 0 && value.compareTo(max) <= 0);
		}

	}

	public record Library(@DefaultValue("true") boolean includePlayedFreeGames) {
	}

	public record Store(@DefaultValue("false") boolean enrichmentEnabled) {
	}

}
