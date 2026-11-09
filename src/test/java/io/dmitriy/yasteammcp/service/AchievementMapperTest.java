package io.dmitriy.yasteammcp.service;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.steamdto.PlayerAchievementsResponse;

class AchievementMapperTest {

	private static PlayerAchievementsResponse.Achievement player(@Nullable String apiname, @Nullable Integer achieved,
			@Nullable Long unlock, @Nullable String name, @Nullable String description) {
		return new PlayerAchievementsResponse.Achievement(apiname, achieved, unlock, name, description);
	}

	@Test
	void toAchievement_whenApiNameBlankOrNull_shouldReturnNull() {
		assertThat(AchievementMapper.toAchievement(player(null, 1, 1L, "n", "d"), null)).isNull();
		assertThat(AchievementMapper.toAchievement(player("  ", 1, 1L, "n", "d"), null)).isNull();
	}

	@Test
	void toAchievement_whenAchievedWithUnlockTime_shouldMapInstant() {
		// When
		var result = AchievementMapper.toAchievement(player("A", 1, 1_700_000_000L, null, null), null);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.achieved()).isTrue();
		assertThat(result.unlockTime()).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
	}

	@Test
	void toAchievement_whenNotAchievedButUnlockTimeSet_shouldDropUnlockTime() {
		var result = AchievementMapper.toAchievement(player("A", 0, 1_700_000_000L, null, null), null);

		assertThat(result).isNotNull();
		assertThat(result.achieved()).isFalse();
		assertThat(result.unlockTime()).isNull();
	}

	@Test
	void toAchievement_whenUnlockTimeZeroOrAchievedNull_shouldHaveNoUnlockTime() {
		assertThat(requireNonNull(AchievementMapper.toAchievement(player("A", 1, 0L, null, null), null)).unlockTime())
			.isNull();
		var unknown = AchievementMapper.toAchievement(player("A", null, 5L, null, null), null);
		assertThat(requireNonNull(unknown).achieved()).isFalse();
		assertThat(unknown.unlockTime()).isNull();
	}

	@Test
	void toAchievement_whenSchemaPresent_shouldPreferSchemaTextAndMapHidden() {
		var schema = new GameSchemaResponse.Achievement("A", "Schema name", "Schema desc", 1);

		var result = requireNonNull(AchievementMapper.toAchievement(player("A", 0, null, "Player name", "Player desc"), schema));

		assertThat(result.displayName()).isEqualTo("Schema name");
		assertThat(result.description()).isEqualTo("Schema desc");
		assertThat(result.hidden()).isTrue();
	}

	@Test
	void toAchievement_whenSchemaTextBlank_shouldFallBackToPlayerText() {
		var schema = new GameSchemaResponse.Achievement("A", " ", "", 0);

		var result = requireNonNull(AchievementMapper.toAchievement(player("A", 0, null, "Player name", "Player desc"), schema));

		assertThat(result.displayName()).isEqualTo("Player name");
		assertThat(result.description()).isEqualTo("Player desc");
		assertThat(result.hidden()).isFalse();
	}

	@Test
	void toAchievement_whenHostileText_shouldSanitizeAndCap() {
		var schema = new GameSchemaResponse.Achievement("A", "Win" + Character.toString(0x202E) + "\nnow\u0000", "d".repeat(900), 0);

		var result = requireNonNull(AchievementMapper.toAchievement(player("A", 0, null, null, null), schema));

		assertThat(result.displayName()).isEqualTo("Win now");
		assertThat(result.description()).hasSize(500).endsWith("\u2026");
	}

	@Test
	void toAchievement_whenSchemaTextOnlyHidden_shouldFallBackToPlayerText() {
		var schema = new GameSchemaResponse.Achievement("A", Character.toString(0x200B) + Character.toString(0x202E), "\u0000", 0);

		var result = requireNonNull(AchievementMapper.toAchievement(player("A", 0, null, "Player name", "Player desc"), schema));

		assertThat(result.displayName()).isEqualTo("Player name");
		assertThat(result.description()).isEqualTo("Player desc");
	}

	@Test
	void toAchievement_whenSchemaTextOnlyInvisibleFormatChars_shouldFallBackToPlayerText() {
		String invisible = new String(new int[] { 0x2060, 0x00AD, 0x061C, 0x2063 }, 0, 4);
		var schema = new GameSchemaResponse.Achievement("A", invisible, invisible, 0);

		var result = requireNonNull(AchievementMapper.toAchievement(player("A", 0, null, "Player name", "Player desc"), schema));

		assertThat(result.displayName()).isEqualTo("Player name");
		assertThat(result.description()).isEqualTo("Player desc");
	}

	@Test
	void toAchievement_whenSchemaTextOnlyHangulFiller_shouldFallBackToPlayerText() {
		String filler = new String(new int[] { 0x3164 }, 0, 1);
		var schema = new GameSchemaResponse.Achievement("A", filler, filler.repeat(3), 0);

		var result = requireNonNull(AchievementMapper.toAchievement(player("A", 0, null, "Player name", "Player desc"), schema));

		assertThat(result.displayName()).isEqualTo("Player name");
		assertThat(result.description()).isEqualTo("Player desc");
	}

	@Test
	void toAchievement_whenNoSchemaAndNoText_shouldLeaveTextAndHiddenNull() {
		var result = requireNonNull(AchievementMapper.toAchievement(player("A", 0, null, " ", null), null));

		assertThat(result.displayName()).isNull();
		assertThat(result.description()).isNull();
		assertThat(result.hidden()).isNull();
	}

}
