package io.dmitriy.yasmcp.config;

import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Java SDK 2.0.0's STDIO transport writes responses through a unicast Reactor sink that is
 * not safe for concurrent emitters: when two tool calls finish on different threads at the same
 * time one response is dropped with "Failed to enqueue message" and the client waits forever
 * (reproduced with three pipelined tools/call requests, D18). Immediate execution runs each
 * handler on the single transport thread, so requests and responses are strictly sequential.
 * This server is local and single-user, so serial tool execution costs nothing that matters.
 */
@Configuration(proxyBeanMethods = false)
public class McpServerConfig {

	@Bean
	McpSyncServerCustomizer serialToolExecutionCustomizer() {
		return spec -> spec.immediateExecution(true);
	}

}
