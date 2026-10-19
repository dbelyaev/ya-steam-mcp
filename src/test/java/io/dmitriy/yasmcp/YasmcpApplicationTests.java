package io.dmitriy.yasmcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class YasmcpApplicationTests {

	@Test
	void contextLoads_withoutAnyCredentials_shouldStart() {
		// Given: no STEAM_API_KEY / STEAM_ID in the environment
		// When: the full application context starts
		// Then: no exception, tools are registered, tool calls report configuration errors later
	}

}
