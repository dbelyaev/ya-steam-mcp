package io.dmitriy.yasteammcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;

class SteamLanguageTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  " })
	void resolve_withoutValue_shouldUseDefault(String requested) {
		// When / Then
		assertThat(SteamLanguage.resolve(requested, "english")).isEqualTo("english");
	}

	@ParameterizedTest
	@ValueSource(strings = { "russian", "Norwegian", " ENGLISH ", "schinese", "koreana" })
	void resolve_withSupportedLanguage_shouldNormalize(String requested) {
		// When
		String resolved = SteamLanguage.resolve(requested, "english");

		// Then
		assertThat(resolved).isEqualTo(requested.trim().toLowerCase());
	}

	@ParameterizedTest
	@ValueSource(strings = { "klingon", "en", "english&key=x", "english\nfoo", "../etc", "ru-RU" })
	void resolve_withUnsupportedLanguage_shouldThrowInvalidArgumentWithoutEchoing(String requested) {
		// When / Then
		assertThatThrownBy(() -> SteamLanguage.resolve(requested, "english")).isInstanceOfSatisfying(SteamException.class,
				ex -> {
					assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
					// "en" is a substring of the example list, so only longer inputs prove non-echo
					if (requested.length() > 2) {
						assertThat(ex.getMessage()).doesNotContain(requested.trim());
					}
				});
	}

	@Test
	void resolve_withUnsupportedLanguage_shouldMentionExamples() {
		// When / Then
		assertThatThrownBy(() -> SteamLanguage.resolve("xx", "english")).hasMessageContaining("english");
	}

}
