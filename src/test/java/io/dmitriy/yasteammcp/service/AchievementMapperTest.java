package io.dmitriy.yasteammcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.steamdto.PlayerAchievementsResponse;

class AchievementMapperTest {

	private static PlayerAchievementsResponse.Achievement player(String apiname, Integer achieved, Long unlock,
			String name, String description) {
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
		assertThat(AchievementMapper.toAchievement(player("A", 1, 0L, null, null), null).unlockTime()).isNull();
		var unknown = AchievementMapper.toAchievement(player("A", null, 5L, null, null), null);
		assertThat(unknown.achieved()).isFalse();
		assertThat(unknown.unlockTime()).isNull();
	}

	@Test
	void toAchievement_whenSchemaPresent_shouldPreferSchemaTextAndMapHidden() {
		var schema = new GameSchemaResponse.Achievement("A", "Schema name", "Schema desc", 1);

		var result = AchievementMapper.toAchievement(player("A", 0, null, "Player name", "Player desc"), schema);

		assertThat(result.displayName()).isEqualTo("Schema name");
		assertThat(result.description()).isEqualTo("Schema desc");
		assertThat(result.hidden()).isTrue();
	}

	@Test
	void toAchievement_whenSchemaTextBlank_shouldFallBackToPlayerText() {
		var schema = new GameSchemaResponse.Achievement("A", " ", "", 0);

		var result = AchievementMapper.toAchievement(player("A", 0, null, "Player name", "Player desc"), schema);

		assertThat(result.displayName()).isEqualTo("Player name");
		assertThat(result.description()).isEqualTo("Player desc");
		assertThat(result.hidden()).isFalse();
	}

	@Test
	void toAchievement_whenNoSchemaAndNoText_shouldLeaveTextAndHiddenNull() {
		var result = AchievementMapper.toAchievement(player("A", 0, null, " ", null), null);

		assertThat(result.displayName()).isNull();
		assertThat(result.description()).isNull();
		assertThat(result.hidden()).isNull();
	}

}
