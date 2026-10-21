package io.dmitriy.yasteammcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Raw process view of the jar: stdout must hold JSON-RPC frames only, secrets must stay out of
 * both streams, the server must start offline and without any configuration, and closing stdin
 * must end the process.
 */
class StdioHygieneIT {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String INITIALIZE = """
			{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"it","version":"1"}}}""";

	private static final String INITIALIZED = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

	private static String call(int id, String tool, String arguments) {
		return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
				+ "\",\"arguments\":" + arguments + "}}";
	}

	private static void assertOnlyJsonRpcFrames(List<String> stdout) {
		assertThat(stdout).isNotEmpty();
		for (String line : stdout) {
			JsonNode frame = MAPPER.readTree(line);
			assertThat(frame.path("jsonrpc").asString()).as("stdout line: %s", line).isEqualTo("2.0");
			assertThat(frame.has("id") || frame.has("method")).as("stdout line: %s", line).isTrue();
		}
	}

	@Test
	void stdout_withHealthyAndFailingCalls_shouldCarryOnlyJsonRpcAndNoSecretsAnywhere() throws Exception {
		// Given
		try (FakeSteam steam = new FakeSteam().start();
				ServerProcess server = ServerProcess.start(Map.of("STEAM_API_KEY", FakeSteam.API_KEY, "STEAM_ID",
						FakeSteam.STEAM_ID, "STEAM_API_BASE_URL", steam.baseUrl(),
						"STEAM_API_ALLOW_INSECURE_TEST_BASE_URL", "true", "STEAM_API_RETRY_DELAY", "1ms"))) {

			// When
			server.send(INITIALIZE);
			server.send(INITIALIZED);
			server.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
			server.send(call(3, "get_library", "{}"));
			server.send(call(4, "get_achievements", "{\"appid\":500}"));
			server.send(call(5, "get_game_news", "{\"appid\":620}"));
			boolean answered = server.awaitStdoutLines(5, Duration.ofSeconds(30));
			server.closeStdin();
			int exit = server.awaitExit(Duration.ofSeconds(20));

			// Then
			assertThat(answered).as("five responses on stdout; got %s lines starting %s; stderr=%s", server.stdoutLines().size(),
					server.stdoutLines().stream().map(l -> l.substring(0, Math.min(60, l.length()))).toList(),
					server.stderrText().substring(Math.max(0, server.stderrText().length() - 2500))).isTrue();
			assertOnlyJsonRpcFrames(server.stdoutLines());
			String everything = String.join("\n", server.stdoutLines()) + "\n" + server.stderrText();
			assertThat(everything).doesNotContain(FakeSteam.API_KEY).doesNotContain(FakeSteam.BODY_SECRET);
			assertThat(server.stderrText()).as("logs go to stderr").contains("tool=get_library outcome=ok");
			assertThat(exit).as("closing stdin ends the process").isZero();
		}
	}

	@Test
	void server_withoutAnyConfiguration_shouldStartOfflineAndReportConfigurationMissing() throws Exception {
		// Given: no STEAM_* variables at all and no network target
		try (ServerProcess server = ServerProcess.start(Map.of())) {

			// When
			server.send(INITIALIZE);
			server.send(INITIALIZED);
			server.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
			server.send(call(3, "get_library", "{}"));
			server.send(call(4, "get_game_news", "{\"appid\":620}"));
			boolean answered = server.awaitStdoutLines(4, Duration.ofSeconds(30));
			server.closeStdin();
			int exit = server.awaitExit(Duration.ofSeconds(20));

			// Then
			assertThat(answered).isTrue();
			assertOnlyJsonRpcFrames(server.stdoutLines());
			JsonNode library = server.stdoutLines().stream().map(MAPPER::readTree).filter(f -> f.path("id").asInt() == 3)
				.findFirst().orElseThrow();
			assertThat(library.at("/result/isError").asBoolean()).isTrue();
			assertThat(library.at("/result/content/0/text").asString()).contains("CONFIGURATION_MISSING");
			assertThat(exit).isZero();
		}
	}

	@Test
	void server_whenStdinCloses_shouldExitPromptlyWithoutOutputAfterTheLastResponse() throws IOException, InterruptedException {
		// Given
		try (ServerProcess server = ServerProcess.start(Map.of())) {
			server.send(INITIALIZE);
			assertThat(server.awaitStdoutLines(1, Duration.ofSeconds(30))).isTrue();

			// When
			long start = System.nanoTime();
			server.closeStdin();
			int exit = server.awaitExit(Duration.ofSeconds(20));
			long elapsedMs = (System.nanoTime() - start) / 1_000_000;

			// Then
			assertThat(exit).isZero();
			assertThat(elapsedMs).isLessThan(15_000);
			assertOnlyJsonRpcFrames(server.stdoutLines());
		}
	}

	@Test
	void pipelinedBurst_shouldAnswerEveryRequestExactlyOnce() throws Exception {
		// Given: many tools/call frames written back to back, including a failing, retried upstream call
		try (FakeSteam steam = new FakeSteam().start();
				ServerProcess server = ServerProcess.start(Map.of("STEAM_API_KEY", FakeSteam.API_KEY, "STEAM_ID",
						FakeSteam.STEAM_ID, "STEAM_API_BASE_URL", steam.baseUrl(),
						"STEAM_API_ALLOW_INSECURE_TEST_BASE_URL", "true", "STEAM_API_RETRY_DELAY", "1ms"))) {
			int calls = 40;
			server.send(INITIALIZE);
			server.send(INITIALIZED);
			for (int i = 0; i < calls; i++) {
				int id = 100 + i;
				server.send(switch (i % 5) {
					case 0 -> call(id, "get_library", "{\"limit\":" + (1 + i) + "}");
					case 1 -> call(id, "get_achievements", "{\"appid\":500}");
					case 2 -> call(id, "get_playtime", "{\"appid\":620}");
					case 3 -> call(id, "get_game_news", "{\"appid\":620,\"limit\":" + (1 + i % 20) + "}");
					default -> call(id, "get_recent_games", "{\"limit\":" + (1 + i) + "}");
				});
			}

			// When
			boolean answered = server.awaitStdoutLines(1 + calls, Duration.ofSeconds(60));
			server.closeStdin();
			int exit = server.awaitExit(Duration.ofSeconds(20));

			// Then
			assertThat(answered).as("responses: %s of %s", server.stdoutLines().size(), 1 + calls).isTrue();
			assertOnlyJsonRpcFrames(server.stdoutLines());
			List<Integer> ids = server.stdoutLines().stream().map(MAPPER::readTree).map(f -> f.path("id").asInt()).sorted().toList();
			assertThat(ids).doesNotHaveDuplicates().contains(1).hasSize(1 + calls);
			assertThat(server.stderrText()).doesNotContain("Failed to enqueue message").doesNotContain(FakeSteam.API_KEY);
			assertThat(exit).isZero();
		}
	}

}
