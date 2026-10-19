package io.dmitriy.yasmcp.tool;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

@Component
public class SteamTools {

	private final ToolResults results;

	public SteamTools(ToolResults results) {
		this.results = results;
	}

	@McpTool(name = "get_library", description = "List visible Steam games owned by the configured Steam user; filter and sort locally.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	public CallToolResult getLibrary() {
		return results.run("get_library", () -> new ToolResponse<>(Map.of("games", List.of()),
				ToolResponse.Meta.of(Instant.now(), false, false, List.of())));
	}

}
