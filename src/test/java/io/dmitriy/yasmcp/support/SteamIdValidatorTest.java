package io.dmitriy.yasmcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;

class SteamIdValidatorTest {

	@Test
	void acceptsSeventeenDigitsUnchanged() {
		assertThat(SteamIdValidator.requireValid("76561197960287930")).isEqualTo("76561197960287930");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "7656119796028793", "765611979602879301", "7656119796028793a", " 76561197960287930",
			"-6561197960287930", "7656119796028793.", "7656119796028793e" })
	void rejectsMalformed(String value) {
		assertThat(SteamIdValidator.isValid(value)).isFalse();
		assertThatThrownBy(() -> SteamIdValidator.requireValid(value)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
	}

	@Test
	void errorMessageDoesNotEchoValue() {
		assertThatThrownBy(() -> SteamIdValidator.requireValid("secret-value-x")).hasMessageNotContaining("secret");
	}

}
