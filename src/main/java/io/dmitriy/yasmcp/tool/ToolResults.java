package io.dmitriy.yasmcp.tool;

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
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

	public CallToolResult run(Supplier<ToolResponse<?>> action) {
		try {
			return success(action.get());
		}
		catch (SteamException ex) {
			return error(ToolError.of(ex.code(), ex.getMessage()));
		}
		catch (Throwable ex) {
			// class name only: message and cause may carry request URLs
			log.error("Unexpected failure in tool: {}", ex.getClass().getName());
			return error(ToolError.of(ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE));
		}
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
