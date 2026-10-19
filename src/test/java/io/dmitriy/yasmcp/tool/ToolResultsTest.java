package io.dmitriy.yasmcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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
	void run_whenActionSucceeds_shouldReturnNonErrorEnvelope() {
		var r = results.run("test_tool", () -> new ToolResponse<>(Map.of("a", 1),
				ToolResponse.Meta.of(Instant.parse("2026-10-19T18:00:00Z"), false, false, List.of())));
		assertThat(r.isError()).isFalse();
		assertThat(text(r)).contains("\"data\":{\"a\":1}")
			.contains("\"fetchedAt\":\"2026-10-19T18:00:00Z\"")
			.contains("\"source\":\"Steam Web API\"");
	}

	@Test
	void run_whenSteamException_shouldReturnErrorEnvelope() {
		var r = results.run("test_tool", () -> {
			throw new SteamException(ErrorCode.UPSTREAM_UNAVAILABLE, "Steam API is temporarily unavailable");
		});
		assertThat(r.isError()).isTrue();
		assertThat(text(r)).isEqualTo(
				"{\"error\":{\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"Steam API is temporarily unavailable\",\"retryable\":true}}");
	}

	@Test
	void run_whenUnexpectedException_shouldNotLeakMessage() {
		var r = results.run("test_tool", () -> {
			throw new IllegalStateException("GET https://api.steampowered.com/x?key=SECRET123 failed");
		});
		assertThat(r.isError()).isTrue();
		assertThat(text(r)).doesNotContain("SECRET123").contains("INTERNAL_ERROR");
	}

	@Test
	void run_whenToolCompletes_shouldLogNameOutcomeAndDurationOnly() {
		// Given
		var appender = new ListAppender<ILoggingEvent>();
		appender.start();
		var logger = (Logger) LoggerFactory.getLogger(ToolResults.class);
		logger.addAppender(appender);
		try {
			// When
			results.run("get_library", () -> {
				throw new SteamException(ErrorCode.UPSTREAM_AUTH_FAILED, "Steam rejected the API key");
			});

			// Then
			assertThat(appender.list).singleElement().satisfies(ev -> assertThat(ev.getFormattedMessage())
				.matches("tool=get_library outcome=UPSTREAM_AUTH_FAILED durationMs=\\d+"));
		}
		finally {
			logger.detachAppender(appender);
		}
	}

}
