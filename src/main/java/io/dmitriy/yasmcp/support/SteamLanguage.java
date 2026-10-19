package io.dmitriy.yasmcp.support;

import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;

/**
 * Steam API language identifiers (the {@code l} parameter). A fixed allowlist: arbitrary
 * input never reaches the outbound request. Unsupported values are rejected, not silently
 * replaced (D17).
 */
public final class SteamLanguage {

	private static final Set<String> SUPPORTED = Set.of("arabic", "bulgarian", "schinese", "tchinese", "czech",
			"danish", "dutch", "english", "finnish", "french", "german", "greek", "hungarian", "indonesian",
			"italian", "japanese", "koreana", "norwegian", "polish", "brazilian", "portuguese", "romanian",
			"russian", "spanish", "latam", "swedish", "thai", "turkish", "ukrainian", "vietnamese");

	private SteamLanguage() {
	}

	public static String resolve(@Nullable String requested, String defaultLanguage) {
		if (requested == null || requested.isBlank()) {
			return defaultLanguage;
		}
		String normalized = requested.trim().toLowerCase(Locale.ROOT);
		if (!SUPPORTED.contains(normalized)) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT,
					"language is not a supported Steam language identifier (for example english, russian, norwegian)");
		}
		return normalized;
	}

}
