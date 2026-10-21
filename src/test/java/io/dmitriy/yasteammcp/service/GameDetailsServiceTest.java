package io.dmitriy.yasteammcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.dmitriy.yasteammcp.TestProperties;
import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.GameSummary;
import io.dmitriy.yasteammcp.model.SteamException;
import io.dmitriy.yasteammcp.model.TransientUpstreamException;
import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;

@ExtendWith(MockitoExtension.class)
class GameDetailsServiceTest {

	@Mock
	LibraryService libraryService;

	@Mock
	SteamApiClient client;

	GameDetailsService service;

	@BeforeEach
	void setUp() {
		service = new GameDetailsService(libraryService, client, TestProperties.withCredentials());
	}

	private static GameSummary owned() {
		return new GameSummary(620, "Portal 2", 740, null, null, null, null, "https://store.steampowered.com/app/620/");
	}

	private static GameSchemaResponse schema(String name, int achievements) {
		var list = java.util.stream.IntStream.range(0, achievements)
			.mapToObj(i -> new GameSchemaResponse.Achievement("A" + i, "n", "d", 0))
			.toList();
		return new GameSchemaResponse(new GameSchemaResponse.Game(name, new GameSchemaResponse.AvailableGameStats(list)));
	}

	@Test
	void getDetails_whenOwnedAndSchemaKnown_shouldCombineBothSourcesWithoutStoreFields() {
		// Given
		when(libraryService.findOwned(620)).thenReturn(Optional.of(owned()));
		when(client.getSchemaForGame(620, "english")).thenReturn(schema("Portal 2 (schema)", 51));

		// When
		var result = service.getDetails(620L, null);

		// Then
		var d = result.data();
		assertThat(d.name()).isEqualTo("Portal 2");
		assertThat(d.ownedOrVisibleInLibrary()).isTrue();
		assertThat(d.playtimeForeverMinutes()).isEqualTo(740);
		assertThat(d.achievementCount()).isEqualTo(51);
		assertThat(d.storeUrl()).isEqualTo("https://store.steampowered.com/app/620/");
		assertThat(d.sources()).containsExactly("GetOwnedGames", "GetSchemaForGame");
		assertThat(d.description()).isNull();
		assertThat(d.genres()).isNull();
		assertThat(d.releaseDate()).isNull();
		assertThat(d.iconUrl()).isNull();
		assertThat(result.partial()).isFalse();
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	void getDetails_whenNotOwnedButSchemaKnown_shouldReturnPartialObjectWithWarning() {
		// Given
		when(libraryService.findOwned(620)).thenReturn(Optional.empty());
		when(client.getSchemaForGame(620, "english")).thenReturn(schema("Portal 2", 3));

		// When
		var result = service.getDetails(620L, null);

		// Then
		assertThat(result.partial()).isTrue();
		assertThat(result.warnings()).singleElement().asString().contains("not in the visible library");
		var d = result.data();
		assertThat(d.ownedOrVisibleInLibrary()).isFalse();
		assertThat(d.name()).isEqualTo("Portal 2");
		assertThat(d.playtimeForeverMinutes()).isNull();
		assertThat(d.achievementCount()).isEqualTo(3);
		assertThat(d.sources()).containsExactly("GetSchemaForGame");
	}

	@Test
	void getDetails_whenOwnedButSchemaFails_shouldReturnPartialWithLibraryDataOnly() {
		// Given
		when(libraryService.findOwned(620)).thenReturn(Optional.of(owned()));
		when(client.getSchemaForGame(620, "english")).thenThrow(new TransientUpstreamException());

		// When
		var result = service.getDetails(620L, null);

		// Then
		assertThat(result.partial()).isTrue();
		assertThat(result.data().achievementCount()).isNull();
		assertThat(result.data().name()).isEqualTo("Portal 2");
		assertThat(result.data().sources()).containsExactly("GetOwnedGames");
		assertThat(result.warnings()).singleElement().asString().contains("schema is unavailable");
	}

	@Test
	void getDetails_whenOwnedWithoutName_shouldFallBackToSchemaName() {
		// Given
		var noName = new GameSummary(620, null, 5, null, null, null, null, "https://store.steampowered.com/app/620/");
		when(libraryService.findOwned(620)).thenReturn(Optional.of(noName));
		when(client.getSchemaForGame(620, "english")).thenReturn(schema("Schema Name", 1));

		// When / Then
		assertThat(service.getDetails(620L, null).data().name()).isEqualTo("Schema Name");
	}

	@Test
	void getDetails_whenNeitherSourceKnowsTheGame_shouldThrowDetailsUnavailable() {
		// Given
		when(libraryService.findOwned(999)).thenReturn(Optional.empty());
		when(client.getSchemaForGame(999, "english")).thenReturn(new GameSchemaResponse(new GameSchemaResponse.Game(null, null)));

		// When / Then
		assertThatThrownBy(() -> service.getDetails(999L, null)).isInstanceOfSatisfying(SteamException.class, ex -> {
			assertThat(ex.code()).isEqualTo(ErrorCode.GAME_DETAILS_UNAVAILABLE);
			assertThat(ex.retryable()).isFalse();
		});
	}

	@Test
	void getDetails_whenNotOwnedAndSchemaCallFailed_shouldKeepTheRetryableUpstreamError() {
		// Given: a transient failure must not be disguised as "no details exist"
		when(libraryService.findOwned(620)).thenReturn(Optional.empty());
		when(client.getSchemaForGame(620, "english")).thenThrow(new TransientUpstreamException());

		// When / Then
		assertThatThrownBy(() -> service.getDetails(620L, null)).isInstanceOfSatisfying(SteamException.class, ex -> {
			assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
			assertThat(ex.retryable()).isTrue();
		});
	}

	@Test
	void getDetails_whenLibraryCallFails_shouldPropagateWithoutCallingSchema() {
		// Given
		when(libraryService.findOwned(620)).thenThrow(new SteamException(ErrorCode.UPSTREAM_AUTH_FAILED, "Steam rejected the API key"));

		// When / Then
		assertThatThrownBy(() -> service.getDetails(620L, null)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_AUTH_FAILED));
		verifyNoInteractions(client);
	}

	@Test
	void getDetails_withLanguage_shouldPassNormalizedLanguageToSchema() {
		// Given
		when(libraryService.findOwned(620)).thenReturn(Optional.of(owned()));
		when(client.getSchemaForGame(620, "norwegian")).thenReturn(schema("x", 0));

		// When
		var d = service.getDetails(620L, "Norwegian").data();

		// Then
		assertThat(d.achievementCount()).isZero();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = { 0, -1, 4294967296L })
	void getDetails_withInvalidAppid_shouldThrowInvalidArgumentWithoutUpstreamCall(Long appid) {
		// When / Then
		assertThatThrownBy(() -> service.getDetails(appid, null)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		verifyNoInteractions(libraryService, client);
	}

	@Test
	void getDetails_withUnsupportedLanguage_shouldThrowInvalidArgumentWithoutUpstreamCall() {
		// When / Then
		assertThatThrownBy(() -> service.getDetails(620L, "klingon")).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		verifyNoInteractions(libraryService, client);
	}

}
