package io.dmitriy.yasteammcp.tool;

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.dmitriy.yasteammcp.client.CacheTrace;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Single place that turns tool outcomes into {@link CallToolResult}. Nothing thrown
 * inside {@link #run} escapes: framework would copy the exception message (which may
 * contain the API key) into the tool result.
 */
@Component
public class ToolResults {

	private static final Logger log = LoggerFactory.getLogger(ToolResults.class);

	private static final String INTERNAL_MESSAGE = "Internal error while handling the request";

	private final JsonMapper jsonMapper;

	public ToolResults(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** Logs tool name, outcome and duration only: never arguments, data or messages. */
	public CallToolResult run(String toolName, Supplier<ToolResponse<?>> action) {
		long start = System.nanoTime();
		try {
			CacheTrace trace = new CacheTrace();
			ToolResponse<?> response = CacheTrace.within(trace, action);
			CallToolResult result = success(trace.allFromCache() ? response.withCached(true) : response);
			logOutcome(toolName, "ok", start);
			return result;
		}
		catch (SteamException ex) {
			logOutcome(toolName, ex.code().name(), start);
			return error(ToolError.of(ex.code(), ex.getMessage()));
		}
		catch (Throwable ex) {
			// class name only: message and cause may carry request URLs
			log.error("Unexpected failure in tool {}: {}", toolName, ex.getClass().getName());
			logOutcome(toolName, ErrorCode.INTERNAL_ERROR.name(), start);
			return error(ToolError.of(ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE));
		}
	}

	private static void logOutcome(String toolName, String outcome, long startNanos) {
		log.info("tool={} outcome={} durationMs={}", toolName, outcome, (System.nanoTime() - startNanos) / 1_000_000);
	}

	public CallToolResult success(ToolResponse<?> response) {
		return CallToolResult.builder().addTextContent(jsonMapper.writeValueAsString(response)).isError(false).build();
	}

	public CallToolResult error(ToolError error) {
		return CallToolResult.builder()
			.addTextContent(jsonMapper.writeValueAsString(Map.of("error", error)))
			.isError(true)
			.build();
	}

}
