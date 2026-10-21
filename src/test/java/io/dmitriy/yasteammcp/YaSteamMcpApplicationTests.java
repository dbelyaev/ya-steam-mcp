package io.dmitriy.yasteammcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.Tool;

@McpSpringBootTest
class YaSteamMcpApplicationTests {

	@Autowired
	McpSyncServer server;

	@Test
	void contextLoads_withoutAnyCredentials_shouldStartAndRegisterTheSixTools() {
		// Given: no STEAM_API_KEY / STEAM_ID in the environment (tool calls will report CONFIGURATION_MISSING later)

		// When: the full application context has started
		var names = server.listTools().stream().map(Tool::name).toList();

		// Then
		assertThat(names).containsExactlyInAnyOrder("get_library", "get_game_details", "get_playtime",
				"get_achievements", "get_recent_games", "get_game_news");
	}

}
