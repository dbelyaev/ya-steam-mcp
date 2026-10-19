package io.dmitriy.yasmcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.GameSummary;
import io.dmitriy.yasmcp.model.LibraryPage;
import io.dmitriy.yasmcp.model.Playtime;
import io.dmitriy.yasmcp.model.RecentGame;
import io.dmitriy.yasmcp.model.RecentGames;
import io.dmitriy.yasmcp.model.Result;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.model.VisibilityStatus;
import io.dmitriy.yasmcp.service.LibraryService;
import io.dmitriy.yasmcp.service.RecentGamesService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SteamToolsTest {

	private static final String STEAM_ID = "76561197960287930";

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Mock
	LibraryService libraryService;

	@Mock
	RecentGamesService recentGamesService;

	SteamTools tools;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);
		tools = new SteamTools(libraryService, recentGamesService, new ToolResults(mapper), clock);
	}

	private JsonNode json(CallToolResult result) {
		return mapper.readTree(((TextContent) result.content().getFirst()).text());
	}

	@Test
	void getLibrary_withArguments_shouldForwardThemRawAndWrapDataWithMeta() {
		// Given
		var game = new GameSummary(620, "Portal 2", 740, null, null, null, null, "https://store.steampowered.com/app/620/");
		var page = new LibraryPage(STEAM_ID, 1, 0, 20, List.of(game), VisibilityStatus.VISIBLE);
		when(libraryService.getLibrary("portal", "NAME_ASC", 1, 2, false)).thenReturn(Result.complete(page));

		// When
		var result = tools.getLibrary("portal", "NAME_ASC", 1, 2, false);

		// Then
		assertThat(result.isError()).isFalse();
		JsonNode body = json(result);
		assertThat(body.at("/data/steamId").asString()).isEqualTo(STEAM_ID);
		assertThat(body.at("/data/visibilityStatus").asString()).isEqualTo("VISIBLE");
		assertThat(body.at("/data/games/0/appid").asLong()).isEqualTo(620);
		assertThat(body.at("/data/games/0/playtimeForeverMinutes").asInt()).isEqualTo(740);
		assertThat(body.at("/data/games/0/playtimeMacForeverMinutes").isNull()).isTrue();
		assertThat(body.at("/meta/source").asString()).isEqualTo("Steam Web API");
		assertThat(body.at("/meta/fetchedAt").asString()).isEqualTo("2026-10-19T18:00:00Z");
		assertThat(body.at("/meta/partial").asBoolean()).isFalse();
	}

	@Test
	void getLibrary_withoutArguments_shouldPassNullsSoServiceAppliesDefaults() {
		// Given
		var page = new LibraryPage(STEAM_ID, 0, 0, 20, List.of(), VisibilityStatus.UNKNOWN);
		when(libraryService.getLibrary(null, null, null, null, null)).thenReturn(Result.complete(page));

		// When
		var result = tools.getLibrary(null, null, null, null, null);

		// Then
		assertThat(result.isError()).isFalse();
		assertThat(json(result).at("/data/visibilityStatus").asString()).isEqualTo("UNKNOWN");
	}

	@Test
	void getLibrary_whenServiceRejectsArgument_shouldReturnErrorEnvelopeWithIsErrorTrue() {
		// Given
		when(libraryService.getLibrary(null, "FOO", null, null, null))
			.thenThrow(new SteamException(ErrorCode.INVALID_ARGUMENT, "sortBy must be one of NAME_ASC"));

		// When
		var result = tools.getLibrary(null, "FOO", null, null, null);

		// Then
		assertThat(result.isError()).isTrue();
		JsonNode body = json(result);
		assertThat(body.at("/error/code").asString()).isEqualTo("INVALID_ARGUMENT");
		assertThat(body.at("/error/retryable").asBoolean()).isFalse();
		assertThat(body.has("data")).isFalse();
	}

	@Test
	void getPlaytime_whenServiceReturnsPlaytime_shouldWrapIt() {
		// Given
		var playtime = new Playtime(620, "Portal 2", 740, null, null, null, Playtime.SOURCE);
		when(libraryService.getPlaytime(620L)).thenReturn(Result.complete(playtime));

		// When
		var result = tools.getPlaytime(620L);

		// Then
		assertThat(result.isError()).isFalse();
		assertThat(json(result).at("/data/source").asString()).isEqualTo("GetOwnedGames");
	}

	@Test
	void getPlaytime_whenGameNotVisible_shouldReturnNonRetryableError() {
		// Given
		when(libraryService.getPlaytime(620L))
			.thenThrow(new SteamException(ErrorCode.GAME_NOT_VISIBLE_OR_NOT_OWNED, "The game is not in the visible library"));

		// When
		var result = tools.getPlaytime(620L);

		// Then
		assertThat(result.isError()).isTrue();
		assertThat(json(result).at("/error/code").asString()).isEqualTo("GAME_NOT_VISIBLE_OR_NOT_OWNED");
	}

	@Test
	void getRecentGames_whenServiceReturnsGames_shouldWrapThem() {
		// Given
		var recent = new RecentGames(STEAM_ID, RecentGames.WINDOW,
				List.of(new RecentGame(620, "Portal 2", 740, 55, "https://store.steampowered.com/app/620/")));
		when(recentGamesService.getRecentGames(5)).thenReturn(Result.complete(recent));

		// When
		var result = tools.getRecentGames(5);

		// Then
		assertThat(result.isError()).isFalse();
		assertThat(json(result).at("/data/games/0/playtimeRecentMinutes").asInt()).isEqualTo(55);
	}

	@Test
	void getRecentGames_whenUpstreamUnavailable_shouldReturnRetryableError() {
		// Given
		when(recentGamesService.getRecentGames(null)).thenThrow(new io.dmitriy.yasmcp.model.TransientUpstreamException());

		// When
		var result = tools.getRecentGames(null);

		// Then
		assertThat(result.isError()).isTrue();
		assertThat(json(result).at("/error/code").asString()).isEqualTo("UPSTREAM_UNAVAILABLE");
		assertThat(json(result).at("/error/retryable").asBoolean()).isTrue();
	}

	@Test
	void getRecentGames_whenUnexpectedExceptionWithSecret_shouldNotLeakIt() {
		// Given
		when(recentGamesService.getRecentGames(null)).thenThrow(new IllegalStateException("GET /x?key=SECRET999"));

		// When
		var result = tools.getRecentGames(null);

		// Then
		assertThat(result.isError()).isTrue();
		verify(recentGamesService).getRecentGames(null);
		assertThat(((TextContent) result.content().getFirst()).text()).doesNotContain("SECRET999")
			.contains("INTERNAL_ERROR");
	}

}
