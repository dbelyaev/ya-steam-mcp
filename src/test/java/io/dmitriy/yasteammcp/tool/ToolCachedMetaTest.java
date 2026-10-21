package io.dmitriy.yasteammcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Ticker;

import io.dmitriy.yasteammcp.TestProperties;
import io.dmitriy.yasteammcp.client.CachedSteamApiClient;
import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.service.AchievementService;
import io.dmitriy.yasteammcp.service.GameDetailsService;
import io.dmitriy.yasteammcp.service.LibraryService;
import io.dmitriy.yasteammcp.service.NewsService;
import io.dmitriy.yasteammcp.service.RecentGamesService;
import io.dmitriy.yasteammcp.steamdto.OwnedGamesResponse;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real cache + service + tool wiring over a mocked HTTP client: meta.cached must follow the cache. */
class ToolCachedMetaTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void getPlaytime_calledTwice_shouldReportCachedFalseThenTrue() {
		// Given
		SteamApiClient http = mock(SteamApiClient.class);
		when(http.getOwnedGames()).thenReturn(new OwnedGamesResponse(new OwnedGamesResponse.Body(1,
				List.of(new OwnedGamesResponse.Game(620, "Portal 2", 740, null, null, null, null, null)))));
		var props = TestProperties.withCredentials();
		var cached = new CachedSteamApiClient(http, props, Ticker.systemTicker());
		var library = new LibraryService(cached, props);
		var tools = new SteamTools(library, mock(RecentGamesService.class), mock(AchievementService.class),
				mock(NewsService.class), mock(GameDetailsService.class), new ToolResults(mapper),
				Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC));

		// When
		JsonNode first = mapper.readTree(((TextContent) tools.getPlaytime(620L).content().getFirst()).text());
		JsonNode second = mapper.readTree(((TextContent) tools.getPlaytime(620L).content().getFirst()).text());

		// Then
		assertThat(first.at("/meta/cached").asBoolean()).isFalse();
		assertThat(second.at("/meta/cached").asBoolean()).isTrue();
		assertThat(second.at("/data/playtimeForeverMinutes").asInt()).isEqualTo(740);
		verify(http, times(1)).getOwnedGames();
	}

}
