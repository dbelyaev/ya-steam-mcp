package io.dmitriy.yasteammcp.config;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.modelcontextprotocol.server.McpServer;

@ExtendWith(MockitoExtension.class)
class McpServerConfigTest {

	@Mock
	McpServer.SyncSpecification<?> spec;

	@Test
	void serialToolExecutionCustomizer_whenApplied_shouldEnableImmediateExecution() {
		// When: guards the D18 fix (concurrent tool completions drop STDIO responses) without a jar run
		new McpServerConfig().serialToolExecutionCustomizer().customize(spec);

		// Then
		verify(spec).immediateExecution(true);
	}

}
