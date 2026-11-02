package io.dmitriy.yasteammcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/** Reads tool results the way every tool test needs to. */
public final class McpResults {

	private McpResults() {
	}

	/** The text of the first content block; every tool answers with exactly one text block. */
	public static String firstText(CallToolResult result) {
		return ((TextContent) result.content().getFirst()).text();
	}

}
