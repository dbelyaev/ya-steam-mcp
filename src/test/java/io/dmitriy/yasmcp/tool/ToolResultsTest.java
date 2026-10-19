package io.dmitriy.yasmcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.json.JsonMapper;

class ToolResultsTest {

	private final ToolResults results = new ToolResults(JsonMapper.builder().build());

	private static String text(CallToolResult r) {
		return ((TextContent) r.content().getFirst()).text();
	}

	@Test
	void successIsNotError() {
		var r = results.run(() -> new ToolResponse<>(Map.of("a", 1),
				ToolResponse.Meta.of(Instant.parse("2026-10-19T18:00:00Z"), false, false, List.of())));
		assertThat(r.isError()).isFalse();
		assertThat(text(r)).contains("\"data\":{\"a\":1}")
			.contains("\"fetchedAt\":\"2026-10-19T18:00:00Z\"")
			.contains("\"source\":\"Steam Web API\"");
	}

	@Test
	void steamExceptionBecomesErrorEnvelope() {
		var r = results.run(() -> {
			throw new SteamException(ErrorCode.UPSTREAM_UNAVAILABLE, "Steam API is temporarily unavailable");
		});
		assertThat(r.isError()).isTrue();
		assertThat(text(r)).isEqualTo(
				"{\"error\":{\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"Steam API is temporarily unavailable\",\"retryable\":true}}");
	}

	@Test
	void unexpectedExceptionNeverLeaksMessage() {
		var r = results.run(() -> {
			throw new IllegalStateException("GET https://api.steampowered.com/x?key=SECRET123 failed");
		});
		assertThat(r.isError()).isTrue();
		assertThat(text(r)).doesNotContain("SECRET123").contains("INTERNAL_ERROR");
	}

}
