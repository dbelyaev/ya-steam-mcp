package io.dmitriy.yasmcp;

import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring AI 2.0.1 registers a STDIO transport for every non-web application, whatever
 * {@code spring.ai.mcp.server.stdio} says (D16). Left alone it would read System.in inside the
 * surefire fork, which surefire uses for its own command channel, and hang JVM exit. This bean
 * makes the auto-configured one back off in Spring tests; the MCP server itself still starts.
 */
@TestConfiguration(proxyBeanMethods = false)
public class QuietMcpTransport {

	@Bean
	McpServerTransportProviderBase quietMcpTransport(JsonMapper jsonMapper) {
		return new StdioServerTransportProvider(new JacksonMcpJsonMapper(jsonMapper), InputStream.nullInputStream(),
				OutputStream.nullOutputStream());
	}

}
