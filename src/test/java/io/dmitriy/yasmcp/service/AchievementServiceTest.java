package io.dmitriy.yasmcp.service;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.dmitriy.yasmcp.TestProperties;
import io.dmitriy.yasmcp.client.SteamApiClient;
import io.dmitriy.yasmcp.model.Achievement;
import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.model.TransientUpstreamException;
import io.dmitriy.yasmcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasmcp.steamdto.PlayerAchievementsResponse;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

	@Mock
	SteamApiClient client;

	AchievementService service;

	@BeforeEach
	void setUp() {
		service = new AchievementService(client, TestProperties.withCredentials());
	}

	private static PlayerAchievementsResponse.Achievement player(String api, int achieved, long unlock) {
		return new PlayerAchievementsResponse.Achievement(api, achieved, unlock, null, null);
	}

	private static PlayerAchievementsResponse stats(PlayerAchievementsResponse.Achievement... list) {
		return new PlayerAchievementsResponse(new PlayerAchievementsResponse.Stats("id", "Game", true, null, List.of(list)));
	}

	private static GameSchemaResponse schema(GameSchemaResponse.Achievement... list) {
		return new GameSchemaResponse(new GameSchemaResponse.Game("Game",
				new GameSchemaResponse.AvailableGameStats(List.of(list))));
	}

	private static GameSchemaResponse.Achievement def(String name, String display, String desc, int hidden) {
		return new GameSchemaResponse.Achievement(name, display, desc, hidden);
	}

	private static SteamException thrown(Runnable call) {
		try {
			call.run();
		}
		catch (SteamException ex) {
			return ex;
		}
		throw new AssertionError("expected SteamException");
	}

	@Test
	void getAchievements_withSchema_shouldJoinTextsHiddenFlagAndIsoUnlockTime() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(stats(player("A", 1, 1735732800L), player("B", 0, 0)));
		when(client.getSchemaForGame(620, "english")).thenReturn(schema(def("A", "First win", "Win once", 0), def("B", "Secret", "???", 1)));

		// When
		var result = service.getAchievements(620L, null, null, null);

		// Then
		var list = result.data();
		assertThat(list.available()).isTrue();
		assertThat(list.total()).isEqualTo(2);
		assertThat(list.unlocked()).isEqualTo(1);
		assertThat(list.returned()).isEqualTo(2);
		assertThat(list.truncated()).isFalse();
		Achievement a = list.achievements().getFirst();
		assertThat(a.id()).isEqualTo("A");
		assertThat(a.displayName()).isEqualTo("First win");
		assertThat(a.description()).isEqualTo("Win once");
		assertThat(a.achieved()).isTrue();
		assertThat(a.unlockTime()).isEqualTo(Instant.parse("2025-01-01T12:00:00Z"));
		assertThat(a.hidden()).isFalse();
		Achievement b = list.achievements().get(1);
		assertThat(b.achieved()).isFalse();
		assertThat(b.unlockTime()).isNull();
		assertThat(b.hidden()).isTrue();
		assertThat(result.partial()).isFalse();
	}

	@Test
	void getAchievements_whenAchievementMissingInSchema_shouldKeepPlayerTextAndNullHidden() {
		// Given
		var withText = new PlayerAchievementsResponse.Achievement("A", 1, 100L, "Player name", "Player desc");
		when(client.getPlayerAchievements(620, "english")).thenReturn(stats(withText));
		when(client.getSchemaForGame(620, "english")).thenReturn(schema(def("OTHER", "x", "y", 0)));

		// When
		var a = service.getAchievements(620L, null, null, null).data().achievements().getFirst();

		// Then
		assertThat(a.displayName()).isEqualTo("Player name");
		assertThat(a.description()).isEqualTo("Player desc");
		assertThat(a.hidden()).isNull();
	}

	@Test
	void getAchievements_withIncludeLockedFalse_shouldReturnOnlyUnlockedButCountAll() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(stats(player("A", 1, 10), player("B", 0, 0), player("C", 1, 20)));
		when(client.getSchemaForGame(620, "english")).thenReturn(schema());

		// When
		var list = service.getAchievements(620L, null, false, null).data();

		// Then
		assertThat(list.achievements()).extracting(Achievement::id).containsExactly("A", "C");
		assertThat(list.total()).isEqualTo(3);
		assertThat(list.unlocked()).isEqualTo(2);
		assertThat(list.returned()).isEqualTo(2);
	}

	@Test
	void getAchievements_whenMoreThanLimit_shouldTruncateAndSayWhatWasReturned() {
		// Given
		when(client.getPlayerAchievements(620, "english"))
			.thenReturn(stats(player("A", 1, 1), player("B", 1, 1), player("C", 1, 1), player("D", 0, 0), player("E", 0, 0)));
		when(client.getSchemaForGame(620, "english")).thenReturn(schema());

		// When
		var list = service.getAchievements(620L, null, null, 2).data();

		// Then
		assertThat(list.total()).isEqualTo(5);
		assertThat(list.returned()).isEqualTo(2);
		assertThat(list.truncated()).isTrue();
		assertThat(list.achievements()).extracting(Achievement::id).containsExactly("A", "B");
	}

	@Test
	void getAchievements_whenSchemaCallFails_shouldReturnPartialWithWarning() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(stats(player("A", 1, 10)));
		when(client.getSchemaForGame(620, "english")).thenThrow(new TransientUpstreamException());

		// When
		var result = service.getAchievements(620L, null, null, null);

		// Then
		assertThat(result.partial()).isTrue();
		assertThat(result.warnings()).singleElement().asString().contains("schema is unavailable");
		assertThat(result.data().achievements()).hasSize(1);
		assertThat(result.data().total()).isEqualTo(1);
	}

	@Test
	void getAchievements_whenSchemaIsEmpty_shouldReturnPartialWithWarning() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(stats(player("A", 1, 10)));
		when(client.getSchemaForGame(620, "english")).thenReturn(new GameSchemaResponse(new GameSchemaResponse.Game(null, null)));

		// When
		var result = service.getAchievements(620L, null, null, null);

		// Then
		assertThat(result.partial()).isTrue();
		assertThat(result.warnings()).hasSize(1);
	}

	@Test
	void getAchievements_whenGameHasNoStats_shouldThrowAchievementsUnavailableWithoutEchoingSteamText() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(new PlayerAchievementsResponse(
				new PlayerAchievementsResponse.Stats(null, null, false, "Requested app has no stats", null)));

		// When
		var ex = thrown(() -> service.getAchievements(620L, null, null, null));

		// Then
		assertThat(ex.code()).isEqualTo(ErrorCode.ACHIEVEMENTS_UNAVAILABLE);
		assertThat(ex.getMessage()).doesNotContain("Requested app");
	}

	@Test
	void getAchievements_whenProfileIsPrivate_shouldThrowDataNotAccessible() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(new PlayerAchievementsResponse(
				new PlayerAchievementsResponse.Stats(null, null, false, "Profile is not public", null)));

		// When
		var ex = thrown(() -> service.getAchievements(620L, null, null, null));

		// Then
		assertThat(ex.code()).isEqualTo(ErrorCode.DATA_NOT_ACCESSIBLE);
		assertThat(ex.retryable()).isFalse();
	}

	@Test
	void getAchievements_whenStatsEnvelopeMissing_shouldThrowAchievementsUnavailable() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(new PlayerAchievementsResponse(null));

		// When / Then
		assertThat(thrown(() -> service.getAchievements(620L, null, null, null)).code())
			.isEqualTo(ErrorCode.ACHIEVEMENTS_UNAVAILABLE);
	}

	@Test
	void getAchievements_whenSuccessButNoList_shouldReturnUnavailableNotZeroOfZero() {
		// Given
		when(client.getPlayerAchievements(620, "english")).thenReturn(new PlayerAchievementsResponse(
				new PlayerAchievementsResponse.Stats("id", "Game", true, null, null)));

		// When
		var result = service.getAchievements(620L, null, null, null);

		// Then
		assertThat(result.data().available()).isFalse();
		assertThat(result.data().achievements()).isEmpty();
		assertThat(result.warnings()).singleElement().asString().contains("zero is not implied");
		verify(client, org.mockito.Mockito.never()).getSchemaForGame(620, "english");
	}

	@Test
	void getAchievements_withLanguage_shouldNormalizeAndUseItForBothCalls() {
		// Given
		when(client.getPlayerAchievements(620, "russian")).thenReturn(stats(player("A", 1, 10)));
		when(client.getSchemaForGame(620, "russian")).thenReturn(schema());

		// When
		service.getAchievements(620L, " Russian ", null, null);

		// Then
		verify(client).getPlayerAchievements(620, "russian");
		verify(client).getSchemaForGame(620, "russian");
	}

	@Test
	void getAchievements_whenEntryHasNoApiName_shouldSkipIt() {
		// Given
		when(client.getPlayerAchievements(620, "english"))
			.thenReturn(stats(new PlayerAchievementsResponse.Achievement(null, 1, 1L, null, null), player("A", 1, 1)));
		when(client.getSchemaForGame(620, "english")).thenReturn(schema());

		// When
		var list = service.getAchievements(620L, null, null, null).data();

		// Then
		assertThat(list.total()).isEqualTo(1);
		assertThat(requireNonNull(list.achievements().getFirst()).id()).isEqualTo("A");
	}

	@Test
	void getAchievements_whenMainCallIsRateLimited_shouldPropagateRetryableError() {
		// Given
		when(client.getPlayerAchievements(620, "english"))
			.thenThrow(new SteamException(ErrorCode.UPSTREAM_RATE_LIMITED, "Steam API rate limit reached"));

		// When
		var ex = thrown(() -> service.getAchievements(620L, null, null, null));

		// Then
		assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_RATE_LIMITED);
		assertThat(ex.retryable()).isTrue();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = { 0, -1, 4294967296L })
	void getAchievements_withInvalidAppid_shouldThrowInvalidArgumentWithoutUpstreamCall(Long appid) {
		// When / Then
		assertThat(thrown(() -> service.getAchievements(appid, null, null, null)).code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
		verifyNoInteractions(client);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 251 })
	void getAchievements_withInvalidLimit_shouldThrowInvalidArgumentWithoutUpstreamCall(int limit) {
		// When / Then
		assertThat(thrown(() -> service.getAchievements(620L, null, null, limit)).code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
		verifyNoInteractions(client);
	}

	@Test
	void getAchievements_withUnsupportedLanguage_shouldThrowInvalidArgumentWithoutUpstreamCall() {
		// When / Then
		assertThatThrownBy(() -> service.getAchievements(620L, "klingon", null, null)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		verifyNoInteractions(client);
	}

}
