package io.dmitriy.yasteammcp.service;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

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
import io.dmitriy.yasteammcp.model.VisibilityStatus;
import io.dmitriy.yasteammcp.steamdto.OwnedGamesResponse;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

	@Mock
	SteamApiClient client;

	LibraryService service;

	@BeforeEach
	void setUp() {
		service = new LibraryService(client, TestProperties.withCredentials());
	}

	private static OwnedGamesResponse.Game game(long appid, String name, Integer playtime) {
		return new OwnedGamesResponse.Game(appid, name, playtime, null, null, null, "hash", null);
	}

	private void owns(OwnedGamesResponse.Game... games) {
		when(client.getOwnedGames()).thenReturn(new OwnedGamesResponse(new OwnedGamesResponse.Body(games.length, List.of(games))));
	}

	private static List<String> names(io.dmitriy.yasteammcp.model.LibraryPage page) {
		return page.games().stream().map(g -> String.valueOf(g.name())).toList();
	}

	// ---- get_library ----

	@Test
	void getLibrary_withDefaults_shouldSortByPlaytimeDescAndMapFields() {
		// Given
		owns(game(10, "Alpha", 5), game(620, "Portal 2", 740), game(30, "Gamma", 0));

		// When
		var page = service.getLibrary(null, null, null, null, null).data();

		// Then
		assertThat(names(page)).containsExactly("Portal 2", "Alpha", "Gamma");
		assertThat(page.steamId()).isEqualTo(TestProperties.STEAM_ID);
		assertThat(page.total()).isEqualTo(3);
		assertThat(page.offset()).isZero();
		assertThat(page.limit()).isEqualTo(20);
		assertThat(page.visibilityStatus()).isEqualTo(VisibilityStatus.VISIBLE);
		GameSummary portal = page.games().getFirst();
		assertThat(portal.appid()).isEqualTo(620);
		assertThat(portal.playtimeForeverMinutes()).isEqualTo(740);
		assertThat(portal.storeUrl()).isEqualTo("https://store.steampowered.com/app/620/");
		assertThat(portal.iconUrl()).isNull();
	}

	@Test
	void getLibrary_withQuery_shouldFilterCaseInsensitivelyAndCountBeforePagination() {
		// Given
		owns(game(1, "Portal", 1), game(2, "Portal 2", 2), game(3, "Half-Life", 3));

		// When
		var page = service.getLibrary("  pOrTaL ", null, 0, 1, null).data();

		// Then
		assertThat(page.total()).isEqualTo(2);
		assertThat(names(page)).containsExactly("Portal 2");
	}

	@Test
	void getLibrary_whenNameIsMissing_shouldNotMatchQueryAndNotFail() {
		// Given
		owns(game(1, null, 1), game(2, "Portal", 2));

		// When
		var page = service.getLibrary("portal", null, null, null, null).data();

		// Then
		assertThat(page.games()).extracting(GameSummary::appid).containsExactly(2L);
	}

	@Test
	void getLibrary_withNameAsc_shouldIgnoreCaseAndBreakTiesByAppid() {
		// Given
		owns(game(3, "beta", 1), game(2, "Alpha", 1), game(1, "alpha", 1));

		// When
		var page = service.getLibrary(null, "NAME_ASC", null, null, null).data();

		// Then
		assertThat(page.games()).extracting(GameSummary::appid).containsExactly(1L, 2L, 3L);
	}

	@Test
	void getLibrary_withNameDesc_shouldReverseNameOrder() {
		// Given
		owns(game(1, "Alpha", 1), game(2, "Beta", 1));

		// When
		var page = service.getLibrary(null, "name_desc", null, null, null).data();

		// Then
		assertThat(names(page)).containsExactly("Beta", "Alpha");
	}

	@Test
	void getLibrary_withPlaytimeAsc_shouldTreatMissingPlaytimeAsZero() {
		// Given
		owns(game(1, "A", 50), game(2, "B", null), game(3, "C", 10));

		// When
		var page = service.getLibrary(null, "PLAYTIME_ASC", null, null, null).data();

		// Then
		assertThat(names(page)).containsExactly("B", "C", "A");
	}

	@Test
	void getLibrary_whenIncludeNeverPlayedFalse_shouldDropZeroAndMissingPlaytime() {
		// Given
		owns(game(1, "A", 0), game(2, "B", null), game(3, "C", 4));

		// When
		var page = service.getLibrary(null, null, null, null, false).data();

		// Then
		assertThat(names(page)).containsExactly("C");
		assertThat(page.total()).isEqualTo(1);
	}

	@Test
	void getLibrary_withOffsetBeyondResults_shouldReturnEmptyPageButKeepTotal() {
		// Given
		owns(game(1, "A", 1), game(2, "B", 2));

		// When
		var page = service.getLibrary(null, null, 50, 10, null).data();

		// Then
		assertThat(page.games()).isEmpty();
		assertThat(page.total()).isEqualTo(2);
		assertThat(page.offset()).isEqualTo(50);
		assertThat(page.visibilityStatus()).isEqualTo(VisibilityStatus.VISIBLE);
	}

	@Test
	void getLibrary_withOffsetAndLimit_shouldReturnRequestedSlice() {
		// Given
		owns(game(1, "A", 4), game(2, "B", 3), game(3, "C", 2), game(4, "D", 1));

		// When
		var page = service.getLibrary(null, null, 1, 2, null).data();

		// Then
		assertThat(names(page)).containsExactly("B", "C");
	}

	@Test
	void getLibrary_whenResponseEmpty_shouldReturnUnknownVisibilityNotEmptyLibraryProof() {
		// Given
		when(client.getOwnedGames()).thenReturn(new OwnedGamesResponse(new OwnedGamesResponse.Body(null, null)));

		// When
		var result = service.getLibrary(null, null, null, null, null);

		// Then
		assertThat(result.data().games()).isEmpty();
		assertThat(result.data().total()).isZero();
		assertThat(result.data().visibilityStatus()).isEqualTo(VisibilityStatus.UNKNOWN);
	}

	@Test
	void getLibrary_whenEnvelopeBodyMissing_shouldTreatAsUnknown() {
		// Given
		when(client.getOwnedGames()).thenReturn(new OwnedGamesResponse(null));

		// When
		var page = service.getLibrary(null, null, null, null, null).data();

		// Then
		assertThat(page.visibilityStatus()).isEqualTo(VisibilityStatus.UNKNOWN);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 101 })
	void getLibrary_withInvalidLimit_shouldThrowInvalidArgumentWithoutUpstreamCall(int limit) {
		// When / Then
		assertThatThrownBy(() -> service.getLibrary(null, null, null, limit, null)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		org.mockito.Mockito.verifyNoInteractions(client);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 100_001 })
	void getLibrary_withInvalidOffset_shouldThrowInvalidArgument(int offset) {
		// When / Then
		assertThatThrownBy(() -> service.getLibrary(null, null, offset, null, null)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
	}

	@Test
	void getLibrary_withUnknownSortBy_shouldThrowInvalidArgumentListingAllowedValues() {
		// When / Then
		assertThatThrownBy(() -> service.getLibrary(null, "FOO", null, null, null)).isInstanceOfSatisfying(SteamException.class,
				ex -> {
					assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
					assertThat(ex.getMessage()).contains("PLAYTIME_DESC");
				});
	}

	@Test
	void getLibrary_withQueryOverHundredChars_shouldThrowInvalidArgument() {
		// When / Then
		assertThatThrownBy(() -> service.getLibrary("x".repeat(101), null, null, null, null))
			.isInstanceOfSatisfying(SteamException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
	}

	@Test
	void getLibrary_whenSteamIdNotConfigured_shouldThrowConfigurationMissingWithoutUpstreamCall() {
		// Given
		var unconfigured = new LibraryService(client, TestProperties.of("k", ""));

		// When / Then
		assertThatThrownBy(() -> unconfigured.getLibrary(null, null, null, null, null)).isInstanceOfSatisfying(
				SteamException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFIGURATION_MISSING));
		org.mockito.Mockito.verifyNoInteractions(client);
	}

	// ---- get_playtime ----

	@Test
	void getPlaytime_whenGameOwned_shouldReturnPlaytimeFromOwnedGames() {
		// Given
		when(client.getOwnedGames()).thenReturn(new OwnedGamesResponse(new OwnedGamesResponse.Body(1,
				List.of(new OwnedGamesResponse.Game(620, "Portal 2", 740, 700, null, 40, null, null)))));

		// When
		var playtime = service.getPlaytime(620L).data();

		// Then
		assertThat(playtime.appid()).isEqualTo(620);
		assertThat(playtime.name()).isEqualTo("Portal 2");
		assertThat(playtime.playtimeForeverMinutes()).isEqualTo(740);
		assertThat(playtime.playtimeWindowsForeverMinutes()).isEqualTo(700);
		assertThat(playtime.playtimeMacForeverMinutes()).isNull();
		assertThat(playtime.playtimeLinuxForeverMinutes()).isEqualTo(40);
		assertThat(playtime.source()).isEqualTo("GetOwnedGames");
	}

	@Test
	void getPlaytime_whenGameAbsent_shouldThrowNotVisibleOrNotOwnedWithoutClaimingNotOwned() {
		// Given
		owns(game(10, "Other", 1));

		// When / Then
		assertThatThrownBy(() -> service.getPlaytime(620L)).isInstanceOfSatisfying(SteamException.class, ex -> {
			assertThat(ex.code()).isEqualTo(ErrorCode.GAME_NOT_VISIBLE_OR_NOT_OWNED);
			assertThat(ex.retryable()).isFalse();
		});
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = { 0, -1, 4294967296L })
	void getPlaytime_withInvalidAppid_shouldThrowInvalidArgumentWithoutUpstreamCall(Long appid) {
		// When / Then
		assertThatThrownBy(() -> service.getPlaytime(appid)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		org.mockito.Mockito.verifyNoInteractions(client);
	}

	@Test
	void getPlaytime_withMaxUint32Appid_shouldSearchLibrary() {
		// Given
		owns(game(4294967295L, "Edge", 1));

		// When
		var playtime = service.getPlaytime(4294967295L).data();

		// Then
		assertThat(requireNonNull(playtime.name())).isEqualTo("Edge");
	}

}
