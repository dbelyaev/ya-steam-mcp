package io.dmitriy.yasteammcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.dmitriy.yasteammcp.model.ErrorCode;

class ToolErrorTest {

	@ParameterizedTest
	@EnumSource(ErrorCode.class)
	void of_withAnyErrorCode_shouldCopyRetryableFromCode(ErrorCode code) {
		var error = ToolError.of(code, "msg");

		assertThat(error.code()).isEqualTo(code);
		assertThat(error.message()).isEqualTo("msg");
		assertThat(error.retryable()).isEqualTo(code.retryable());
	}

}
