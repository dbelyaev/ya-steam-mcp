package io.dmitriy.yasmcp.tool;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.dmitriy.yasmcp.McpSpringBootTest;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/** What an MCP client sees in {@code tools/list}: names, required arguments, read-only hints. */
@McpSpringBootTest
class SteamToolsContractTest {

	@Autowired
	McpSyncServer server;

	private Tool tool(String name) {
		return server.listTools().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private static List<String> required(Tool tool) {
		Object required = tool.inputSchema().get("required");
		return required == null ? List.of() : (List<String>) required;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> property(Tool tool, String name) {
		var properties = (Map<String, Map<String, Object>>) requireNonNull(tool.inputSchema().get("properties"));
		return requireNonNull(properties.get(name));
	}

	@Test
	void listTools_shouldExposeCoreToolsWithReadOnlyHints() {
		// When
		var tools = server.listTools();

		// Then
		assertThat(tools).extracting(Tool::name).contains("get_library", "get_playtime", "get_recent_games");
		tools.forEach(t -> {
			assertThat(t.annotations().readOnlyHint()).as(t.name()).isTrue();
			assertThat(t.annotations().destructiveHint()).as(t.name()).isFalse();
			assertThat(t.annotations().idempotentHint()).as(t.name()).isTrue();
			assertThat(t.annotations().openWorldHint()).as(t.name()).isTrue();
		});
	}

	@Test
	void getLibrary_inputSchema_shouldHaveNoRequiredArguments() {
		// When
		Tool tool = tool("get_library");

		// Then
		assertThat(required(tool)).isEmpty();
		assertThat(property(tool, "query").get("type")).isEqualTo("string");
		assertThat(property(tool, "sortBy").get("type")).isEqualTo("string");
		assertThat(property(tool, "offset").get("type")).isEqualTo("integer");
		assertThat(property(tool, "limit").get("type")).isEqualTo("integer");
		assertThat(property(tool, "includeNeverPlayed").get("type")).isEqualTo("boolean");
	}

	@Test
	void getPlaytime_inputSchema_shouldRequireIntegerAppid() {
		// When
		Tool tool = tool("get_playtime");

		// Then
		assertThat(required(tool)).containsExactly("appid");
		assertThat(property(tool, "appid").get("type")).isEqualTo("integer");
	}

	@Test
	void getRecentGames_inputSchema_shouldHaveOptionalLimitOnly() {
		// When
		Tool tool = tool("get_recent_games");

		// Then
		assertThat(required(tool)).isEmpty();
		assertThat(property(tool, "limit").get("type")).isEqualTo("integer");
	}

	@Test
	void getAchievements_inputSchema_shouldRequireAppidOnly() {
		// When
		Tool tool = tool("get_achievements");

		// Then
		assertThat(required(tool)).containsExactly("appid");
		assertThat(property(tool, "language").get("type")).isEqualTo("string");
		assertThat(property(tool, "includeLocked").get("type")).isEqualTo("boolean");
		assertThat(property(tool, "limit").get("type")).isEqualTo("integer");
	}

}
