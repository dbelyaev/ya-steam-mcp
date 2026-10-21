package io.dmitriy.yasteammcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;

class AppIdValidatorTest {

	@ParameterizedTest
	@ValueSource(longs = { 1, 620, 2147483648L, 4294967295L })
	void requireValid_withinUint32_shouldReturnValue(long appid) {
		// When
		long result = AppIdValidator.requireValid(appid);

		// Then
		assertThat(result).isEqualTo(appid);
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1, 4294967296L, Long.MAX_VALUE, Long.MIN_VALUE })
	void requireValid_outOfRange_shouldThrowInvalidArgument(long appid) {
		// When / Then
		assertThatThrownBy(() -> AppIdValidator.requireValid(appid)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
	}

	@Test
	void requireValid_whenNull_shouldThrow() {
		// When / Then
		assertThatThrownBy(() -> AppIdValidator.requireValid(null)).isInstanceOf(SteamException.class);
	}

}
