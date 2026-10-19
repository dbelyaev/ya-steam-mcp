package io.dmitriy.yasmcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;

class AppIdValidatorTest {

	@ParameterizedTest
	@ValueSource(longs = { 1, 620, 2147483648L, 4294967295L })
	void requireValid_withinUint32_shouldReturnValue(long appid) {
		assertThat(AppIdValidator.requireValid(appid)).isEqualTo(appid);
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1, 4294967296L, Long.MAX_VALUE, Long.MIN_VALUE })
	void requireValid_outOfRange_shouldThrowInvalidArgument(long appid) {
		assertThatThrownBy(() -> AppIdValidator.requireValid(appid)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
	}

	@Test
	void requireValid_whenNull_shouldThrow() {
		assertThatThrownBy(() -> AppIdValidator.requireValid(null)).isInstanceOf(SteamException.class);
	}

}
