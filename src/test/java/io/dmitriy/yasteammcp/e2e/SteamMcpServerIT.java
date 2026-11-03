package io.dmitriy.yasteammcp.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.modelcontextprotocol.client.McpClient;
import io.dmitriy.yasteammcp.McpResults;
import io.dmitriy.yasteammcp.TestTools;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The built jar driven by the MCP SDK client over real STDIO, with Steam stubbed by WireMock.
 * Covers handshake, discovery, every tool, caching, failure isolation and argument validation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SteamMcpServerIT {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static FakeSteam steam;

	private static McpSyncClient client;

	private static final List<String> STDERR = Collections.synchronizedList(new ArrayList<>());

	private static final List<String> ALL_RESULT_TEXT = Collections.synchronizedList(new ArrayList<>());

	@BeforeAll
	static void start() {
		steam = new FakeSteam().start();
		ServerParameters params = ServerParameters.builder(ServerProcess.command().getFirst())
			.args(ServerProcess.command().subList(1, ServerProcess.command().size()))
			.env(steam.serverEnv())
			.build();
		var transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(MAPPER));
		transport.setStdErrorHandler(STDERR::add);
		client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
		client.initialize();
	}

	@AfterAll
	static void stop() {
		client.closeGracefully();
		steam.close();
	}

	private static CallToolResult call(String tool, Map<String, Object> arguments) {
		CallToolResult result = client.callTool(new CallToolRequest(tool, arguments));
		ALL_RESULT_TEXT.add(text(result));
		return result;
	}

	private static String text(CallToolResult result) {
		return McpResults.firstText(result);
	}

	private static JsonNode body(CallToolResult result) {
		return MAPPER.readTree(text(result));
	}

	@Test
	@Order(1)
	void initialize_whenConnecting_shouldReportServerNameAndToolsCapability() {
		// When
		var init = client.initialize();

		// Then
		assertThat(init.serverInfo().name()).isEqualTo("ya-steam-mcp");
		assertThat(init.serverInfo().version()).as("filtered from the pom, not a literal placeholder")
			.matches("\\d+\\.\\d+\\.\\d+.*");
		assertThat(init.capabilities().tools()).isNotNull();
	}

	@Test
	@Order(2)
	void listTools_whenDiscovering_shouldExposeExactlyTheSixReadOnlyMvpTools() {
		// When
		List<Tool> tools = client.listTools().tools();

		// Then
		assertThat(tools).extracting(Tool::name)
			.containsExactlyInAnyOrderElementsOf(TestTools.NAMES);
		tools.forEach(tool -> {
			assertThat(tool.inputSchema()).as(tool.name()).containsEntry("type", "object");
			assertThat(tool.annotations().readOnlyHint()).as(tool.name()).isTrue();
			assertThat(tool.annotations().destructiveHint()).as(tool.name()).isFalse();
		});
	}

	@Test
	@Order(3)
	void callTool_forEachOfTheSixTools_shouldReturnStructuredDataWithMeta() {
		// When
		var library = body(call("get_library", Map.of("query", "portal")));
		var playtime = body(call("get_playtime", Map.of("appid", 620)));
		var recent = body(call("get_recent_games", Map.of()));
		var achievements = body(call("get_achievements", Map.of("appid", 620, "language", "english")));
		var news = body(call("get_game_news", Map.of("appid", 620, "maxContentChars", 100)));
		var details = body(call("get_game_details", Map.of("appid", 620)));

		// Then
		assertThat(library.at("/data/games/0/name").asString()).isEqualTo("Portal 2");
		assertThat(library.at("/data/visibilityStatus").asString()).isEqualTo("VISIBLE");
		assertThat(library.at("/data/steamId").asString()).isEqualTo(FakeSteam.STEAM_ID);
		assertThat(playtime.at("/data/playtimeForeverMinutes").asInt()).isEqualTo(740);
		assertThat(recent.at("/data/games/0/playtimeRecentMinutes").asInt()).isEqualTo(55);
		assertThat(achievements.at("/data/total").asInt()).isEqualTo(2);
		assertThat(achievements.at("/data/achievements/0/displayName").asString()).isEqualTo("Wake Up Call");
		assertThat(achievements.at("/data/achievements/0/unlockTime").asString()).isEqualTo("2025-01-01T12:00:00Z");
		assertThat(news.at("/data/items/0/title").asString()).isEqualTo("Big update");
		assertThat(news.at("/data/items/0/contentPreview").asString()).isEqualTo("Patch notes");
		assertThat(news.at("/data/items/0/url").isNull()).as("non-https link is dropped").isTrue();
		assertThat(details.at("/data/achievementCount").asInt()).isEqualTo(2);
		assertThat(details.at("/data/description").isNull()).isTrue();
		for (JsonNode response : List.of(library, playtime, recent, achievements, news, details)) {
			assertThat(response.at("/meta/source").asString()).isEqualTo("Steam Web API");
			assertThat(response.at("/meta/fetchedAt").asString()).endsWith("Z");
		}
	}

	@Test
	@Order(4)
	void repeatedCall_withSameArguments_shouldBeServedFromCacheAndReportIt() {
		// Given: a count not used by earlier tests
		Map<String, Object> args = Map.of("limit", 3);
		int before = steam.server().findAll(getRequestedFor(urlPathEqualTo("/IPlayerService/GetRecentlyPlayedGames/v1/"))).size();

		// When
		var first = body(call("get_recent_games", args));
		var second = body(call("get_recent_games", args));

		// Then
		assertThat(first.at("/meta/cached").asBoolean()).isFalse();
		assertThat(second.at("/meta/cached").asBoolean()).isTrue();
		int after = steam.server().findAll(getRequestedFor(urlPathEqualTo("/IPlayerService/GetRecentlyPlayedGames/v1/"))).size();
		assertThat(after - before).isEqualTo(1);
	}

	@Test
	@Order(5)
	void toolFailure_whenOneCallFails_shouldNotAffectTheNextCall() {
		// Given: appid 777 is not in the stubbed library
		var failed = call("get_playtime", Map.of("appid", 777));

		// When
		var good = call("get_library", Map.of("limit", 1));

		// Then
		assertThat(failed.isError()).isTrue();
		assertThat(body(failed).at("/error/code").asString()).isEqualTo("GAME_NOT_VISIBLE_OR_NOT_OWNED");
		assertThat(good.isError()).isFalse();
		assertThat(body(good).at("/data/games").size()).isEqualTo(1);
	}

	@Test
	@Order(6)
	void upstreamServerError_whenSteamKeepsFailing_shouldBeRetriedTwiceThenReturnRetryableErrorAndServerRecovers() {
		// When
		var failed = call("get_achievements", Map.of("appid", 500));
		var good = call("get_playtime", Map.of("appid", 620));

		// Then
		assertThat(failed.isError()).isTrue();
		JsonNode error = body(failed).at("/error");
		assertThat(error.at("/code").asString()).isEqualTo("UPSTREAM_UNAVAILABLE");
		assertThat(error.at("/retryable").asBoolean()).isTrue();
		assertThat(steam.server().findAll(getRequestedFor(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/"))
			.withQueryParam("appid", equalTo("500")))).hasSize(3);
		assertThat(good.isError()).isFalse();
	}

	@Test
	@Order(7)
	void getAchievements_forGameWithoutStats_shouldReturnUnavailableNotZeroOfZero() {
		// When
		var result = call("get_achievements", Map.of("appid", 10));

		// Then
		assertThat(result.isError()).isTrue();
		assertThat(body(result).at("/error/code").asString()).isEqualTo("ACHIEVEMENTS_UNAVAILABLE");
	}

	@Test
	@Order(8)
	void toolCall_withArgumentsOutOfRange_shouldReturnInvalidArgumentEnvelopeWithoutCallingSteam() {
		// Given
		int before = steam.server().getAllServeEvents().size();

		// When
		var zero = call("get_playtime", Map.of("appid", 0));
		var tooBig = call("get_playtime", Map.of("appid", 4294967296L));
		var badLimit = call("get_library", Map.of("limit", 101));
		var badSort = call("get_library", Map.of("sortBy", "FOO"));
		var badLanguage = call("get_achievements", Map.of("appid", 620, "language", "klingon"));

		// Then
		for (CallToolResult result : List.of(zero, tooBig, badLimit, badSort, badLanguage)) {
			assertThat(result.isError()).isTrue();
			assertThat(body(result).at("/error/code").asString()).isEqualTo("INVALID_ARGUMENT");
			assertThat(body(result).at("/error/retryable").asBoolean()).isFalse();
		}
		assertThat(steam.server().getAllServeEvents()).hasSize(before);
	}

	@Test
	@Order(9)
	void toolCall_withArgumentsOfWrongShape_shouldBeRejectedByTheFrameworkAsToolErrors() {
		// When
		var missing = call("get_playtime", Map.of());
		var wrongType = call("get_playtime", Map.of("appid", "abc"));

		// Then
		for (CallToolResult result : List.of(missing, wrongType)) {
			assertThat(result.isError()).isTrue();
			assertThat(text(result)).contains("input validation failed");
		}
		assertThat(call("get_playtime", Map.of("appid", 620)).isError()).isFalse();
	}

	@Test
	@Order(99)
	void toolCalls_ofAnyOutcome_shouldNeverExposeApiKeyOrUpstreamBodyInResultsOrServerStderr() {
		// Given: own representative calls (success, upstream failure, validation error), so this test also passes
		// when run alone; when the whole class runs it additionally covers everything the earlier tests collected
		call("get_library", Map.of("limit", 1));
		// appid 500 is the one stub that answers 500 with BODY_SECRET and the API key in its body
		var upstreamFailure = call("get_achievements", Map.of("appid", 500));
		call("get_playtime", Map.of("appid", 0));

		// Then: the upstream failure really happened, so the leak assertions below are not vacuous
		assertThat(upstreamFailure.isError()).isTrue();
		assertThat(body(upstreamFailure).at("/error/code").asString()).isEqualTo("UPSTREAM_UNAVAILABLE");
		assertThat(steam.server().findAll(getRequestedFor(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/"))
			.withQueryParam("appid", equalTo("500")))).isNotEmpty();

		String everything = String.join("\n", ALL_RESULT_TEXT) + "\n" + String.join("\n", STDERR);
		assertThat(ALL_RESULT_TEXT).isNotEmpty();
		assertThat(everything).doesNotContain(FakeSteam.API_KEY).doesNotContain(FakeSteam.BODY_SECRET).doesNotContain("key=");
		assertThat(requireNonNull(STDERR)).anyMatch(line -> line.contains("tool=get_library outcome=ok"));
	}

}
