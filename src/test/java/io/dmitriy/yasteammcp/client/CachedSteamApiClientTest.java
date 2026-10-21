package io.dmitriy.yasteammcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.dmitriy.yasteammcp.TestProperties;
import io.dmitriy.yasteammcp.config.SteamProperties;
import io.dmitriy.yasteammcp.model.TransientUpstreamException;
import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.steamdto.NewsResponse;
import io.dmitriy.yasteammcp.steamdto.OwnedGamesResponse;
import io.dmitriy.yasteammcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasteammcp.steamdto.RecentlyPlayedResponse;

@ExtendWith(MockitoExtension.class)
class CachedSteamApiClientTest {

	@Mock
	SteamApiClient delegate;

	FakeTicker ticker = new FakeTicker();

	CachedSteamApiClient client;

	@BeforeEach
	void setUp() {
		client = new CachedSteamApiClient(delegate, TestProperties.withCredentials(), ticker);
	}

	private static OwnedGamesResponse owned(int count) {
		var games = java.util.stream.IntStream.range(0, count)
			.mapToObj(i -> new OwnedGamesResponse.Game(i + 1, "g" + i, 1, null, null, null, null, null))
			.toList();
		return new OwnedGamesResponse(new OwnedGamesResponse.Body(count, count == 0 ? null : games));
	}

	private static PlayerAchievementsResponse achievements(boolean success, int count) {
		var list = java.util.stream.IntStream.range(0, count)
			.mapToObj(i -> new PlayerAchievementsResponse.Achievement("A" + i, 1, 1L, null, null))
			.toList();
		return new PlayerAchievementsResponse(
				new PlayerAchievementsResponse.Stats("id", "g", success, null, count == 0 ? null : list));
	}

	@Test
	void getOwnedGames_calledTwice_shouldHitUpstreamOnce() {
		// Given
		var response = owned(2);
		when(delegate.getOwnedGames()).thenReturn(response);

		// When
		var first = client.getOwnedGames();
		var second = client.getOwnedGames();

		// Then
		assertThat(first).isSameAs(response);
		assertThat(second).isSameAs(response);
		verify(delegate, times(1)).getOwnedGames();
	}

	@Test
	void getOwnedGames_afterTtl_shouldReloadFromUpstream() {
		// Given
		when(delegate.getOwnedGames()).thenReturn(owned(1));
		client.getOwnedGames();

		// When: default TTL is 5 minutes
		ticker.advance(Duration.ofMinutes(4));
		client.getOwnedGames();
		ticker.advance(Duration.ofMinutes(2));
		client.getOwnedGames();

		// Then
		verify(delegate, times(2)).getOwnedGames();
	}

	@Test
	void getOwnedGames_whenEmptyAnswer_shouldNotCacheIt() {
		// Given: an empty list may be a privacy blip, so it must not be pinned
		when(delegate.getOwnedGames()).thenReturn(owned(0), owned(3));

		// When
		var first = client.getOwnedGames();
		var second = client.getOwnedGames();

		// Then
		verify(delegate, times(2)).getOwnedGames();
		assertThat(first.response()).isNotNull();
		assertThat(second.response()).isNotNull();
	}

	@Test
	void getOwnedGames_whenUpstreamFails_shouldNotCacheTheFailure() {
		// Given
		var ok = owned(1);
		when(delegate.getOwnedGames()).thenThrow(new TransientUpstreamException()).thenReturn(ok);

		// When / Then
		assertThatThrownBy(client::getOwnedGames).isInstanceOf(TransientUpstreamException.class);
		assertThat(client.getOwnedGames()).isSameAs(ok);
		assertThat(client.getOwnedGames()).isSameAs(ok);
		verify(delegate, times(2)).getOwnedGames();
	}

	@Test
	void getPlayerAchievements_shouldKeyByAppidAndLanguage() {
		// Given
		when(delegate.getPlayerAchievements(620, "english")).thenReturn(achievements(true, 1));
		when(delegate.getPlayerAchievements(620, "russian")).thenReturn(achievements(true, 2));
		when(delegate.getPlayerAchievements(730, "english")).thenReturn(achievements(true, 3));

		// When
		client.getPlayerAchievements(620, "english");
		client.getPlayerAchievements(620, "english");
		client.getPlayerAchievements(620, "russian");
		client.getPlayerAchievements(730, "english");

		// Then
		verify(delegate, times(1)).getPlayerAchievements(620, "english");
		verify(delegate, times(1)).getPlayerAchievements(620, "russian");
		verify(delegate, times(1)).getPlayerAchievements(730, "english");
	}

	@Test
	void getPlayerAchievements_whenSuccessFalseOrEmpty_shouldNotCache() {
		// Given
		when(delegate.getPlayerAchievements(620, "english")).thenReturn(achievements(false, 0), achievements(true, 0),
				achievements(true, 2));

		// When
		client.getPlayerAchievements(620, "english");
		client.getPlayerAchievements(620, "english");
		client.getPlayerAchievements(620, "english");
		client.getPlayerAchievements(620, "english");

		// Then: two uncached answers, then one stored answer served for the last call
		verify(delegate, times(3)).getPlayerAchievements(620, "english");
	}

	@Test
	void endpoints_shouldKeepTheirOwnTtl() {
		// Given: achievements 10 min, schema 24 h
		when(delegate.getPlayerAchievements(620, "english")).thenReturn(achievements(true, 1));
		when(delegate.getSchemaForGame(620, "english")).thenReturn(new GameSchemaResponse(new GameSchemaResponse.Game("g", null)));
		client.getPlayerAchievements(620, "english");
		client.getSchemaForGame(620, "english");

		// When
		ticker.advance(Duration.ofHours(1));
		client.getPlayerAchievements(620, "english");
		client.getSchemaForGame(620, "english");

		// Then
		verify(delegate, times(2)).getPlayerAchievements(620, "english");
		verify(delegate, times(1)).getSchemaForGame(620, "english");
	}

	@Test
	void getSchemaForGame_whenEmptyGame_shouldNotCache() {
		// Given
		when(delegate.getSchemaForGame(1, "english")).thenReturn(new GameSchemaResponse(new GameSchemaResponse.Game(null, null)));

		// When
		client.getSchemaForGame(1, "english");
		client.getSchemaForGame(1, "english");

		// Then
		verify(delegate, times(2)).getSchemaForGame(1, "english");
	}

	@Test
	void getNewsForApp_shouldKeyByCountAndMaxLength() {
		// Given
		var news = new NewsResponse(new NewsResponse.AppNews(620L, List.of()));
		when(delegate.getNewsForApp(620, 5, 100)).thenReturn(news);
		when(delegate.getNewsForApp(620, 5, null)).thenReturn(news);

		// When
		client.getNewsForApp(620, 5, 100);
		client.getNewsForApp(620, 5, 100);
		client.getNewsForApp(620, 5, null);
		client.getNewsForApp(620, 5, null);

		// Then
		verify(delegate, times(1)).getNewsForApp(620, 5, 100);
		verify(delegate, times(1)).getNewsForApp(620, 5, null);
	}

	@Test
	void getRecentlyPlayedGames_shouldCacheByCountForTwoMinutes() {
		// Given
		when(delegate.getRecentlyPlayedGames(5)).thenReturn(new RecentlyPlayedResponse(new RecentlyPlayedResponse.Body(0, null)));
		client.getRecentlyPlayedGames(5);
		client.getRecentlyPlayedGames(5);

		// When
		ticker.advance(Duration.ofMinutes(3));
		client.getRecentlyPlayedGames(5);

		// Then
		verify(delegate, times(2)).getRecentlyPlayedGames(5);
	}

	@Test
	void anyEndpoint_whenCacheDisabled_shouldAlwaysCallUpstream() {
		// Given
		SteamProperties base = TestProperties.withCredentials();
		var disabled = new SteamProperties(base.api(), base.library(), base.store(), base.cache().disabled());
		var uncached = new CachedSteamApiClient(delegate, disabled, ticker);
		when(delegate.getOwnedGames()).thenReturn(owned(1));

		// When
		uncached.getOwnedGames();
		uncached.getOwnedGames();

		// Then
		verify(delegate, times(2)).getOwnedGames();
	}

	@Test
	void trace_withMixedHitsAndMisses_shouldReportAllFromCacheOnlyWhenEveryReadWasAHit() {
		// Given
		when(delegate.getOwnedGames()).thenReturn(owned(1));
		when(delegate.getRecentlyPlayedGames(5)).thenReturn(new RecentlyPlayedResponse(new RecentlyPlayedResponse.Body(0, null)));
		client.getOwnedGames();

		// When
		var allHits = new CacheTrace();
		CacheTrace.within(allHits, client::getOwnedGames);
		var mixed = new CacheTrace();
		CacheTrace.within(mixed, () -> {
			client.getOwnedGames();
			return client.getRecentlyPlayedGames(5);
		});
		var none = new CacheTrace();

		// Then
		assertThat(allHits.allFromCache()).isTrue();
		assertThat(mixed.allFromCache()).isFalse();
		assertThat(none.allFromCache()).isFalse();
	}

}
