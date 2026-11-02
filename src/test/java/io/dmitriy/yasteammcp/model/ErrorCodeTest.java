package io.dmitriy.yasteammcp.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

	@Test
	void retryable_acrossAllCodes_shouldBeTrueOnlyForRateLimitAndUnavailable() {
		// When
		var retryable = Arrays.stream(ErrorCode.values()).filter(ErrorCode::retryable).toList();

		// Then: a newly added code defaults to non-retryable unless it is consciously added here
		assertThat(retryable).containsExactlyInAnyOrder(ErrorCode.UPSTREAM_RATE_LIMITED,
				ErrorCode.UPSTREAM_UNAVAILABLE);
	}

}
