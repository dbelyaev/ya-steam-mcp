package io.dmitriy.yasmcp.tool;

import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class SteamTools {

	@McpTool(name = "get_library", description = "List visible Steam games owned by the configured Steam user; filter and sort locally.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	public Map<String, Object> getLibrary() {
		return Map.of("data", Map.of("games", java.util.List.of()));
	}

}
