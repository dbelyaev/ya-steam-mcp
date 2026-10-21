package io.dmitriy.yasteammcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.dmitriy.yasteammcp.TestProperties;
import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;
import io.dmitriy.yasteammcp.steamdto.RecentlyPlayedResponse;

@ExtendWith(MockitoExtension.class)
class RecentGamesServiceTest {

	@Mock
	SteamApiClient client;

	RecentGamesService service;

	@BeforeEach
	void setUp() {
		service = new RecentGamesService(client, TestProperties.withCredentials());
	}

	@Test
	void getRecentGames_withoutLimit_shouldRequestDefaultTwentyAndMapFields() {
		// Given
		when(client.getRecentlyPlayedGames(20)).thenReturn(new RecentlyPlayedResponse(new RecentlyPlayedResponse.Body(1,
				List.of(new RecentlyPlayedResponse.Game(620, "Portal 2", 55, 740, "hash")))));

		// When
		var recent = service.getRecentGames(null).data();

		// Then
		assertThat(recent.steamId()).isEqualTo(TestProperties.STEAM_ID);
		assertThat(recent.windowDescription()).contains("recent activity");
		var game = recent.games().getFirst();
		assertThat(game.appid()).isEqualTo(620);
		assertThat(game.playtimeRecentMinutes()).isEqualTo(55);
		assertThat(game.playtimeForeverMinutes()).isEqualTo(740);
		assertThat(game.storeUrl()).isEqualTo("https://store.steampowered.com/app/620/");
	}

	@Test
	void getRecentGames_withLimit_shouldPassCountUpstreamAndTruncateOversizedAnswer() {
		// Given: upstream ignores count and returns more than asked
		when(client.getRecentlyPlayedGames(1)).thenReturn(new RecentlyPlayedResponse(new RecentlyPlayedResponse.Body(2,
				List.of(new RecentlyPlayedResponse.Game(1, "A", 1, 1, null), new RecentlyPlayedResponse.Game(2, "B", 1, 1, null)))));

		// When
		var recent = service.getRecentGames(1).data();

		// Then
		verify(client).getRecentlyPlayedGames(1);
		assertThat(recent.games()).hasSize(1);
	}

	@Test
	void getRecentGames_whenNoRecentActivity_shouldReturnEmptyListNotError() {
		// Given
		when(client.getRecentlyPlayedGames(20)).thenReturn(new RecentlyPlayedResponse(new RecentlyPlayedResponse.Body(0, null)));

		// When
		var result = service.getRecentGames(null);

		// Then
		assertThat(result.data().games()).isEmpty();
		assertThat(result.partial()).isFalse();
	}

	@Test
	void getRecentGames_whenEnvelopeBodyMissing_shouldReturnEmptyList() {
		// Given
		when(client.getRecentlyPlayedGames(20)).thenReturn(new RecentlyPlayedResponse(null));

		// When
		var recent = service.getRecentGames(null).data();

		// Then
		assertThat(recent.games()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 101 })
	void getRecentGames_withInvalidLimit_shouldThrowInvalidArgumentWithoutUpstreamCall(int limit) {
		// When / Then
		assertThatThrownBy(() -> service.getRecentGames(limit)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		verifyNoInteractions(client);
	}

	@Test
	void getRecentGames_whenSteamIdNotConfigured_shouldThrowConfigurationMissing() {
		// Given
		var unconfigured = new RecentGamesService(client, TestProperties.of("k", ""));

		// When / Then
		assertThatThrownBy(() -> unconfigured.getRecentGames(null)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFIGURATION_MISSING));
		verifyNoInteractions(client);
	}

}
