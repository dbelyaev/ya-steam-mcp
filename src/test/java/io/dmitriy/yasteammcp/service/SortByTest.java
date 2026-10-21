package io.dmitriy.yasteammcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.GameSummary;
import io.dmitriy.yasteammcp.model.SteamException;

class SortByTest {

	private static GameSummary game(long appid, String name, Integer playtime) {
		return new GameSummary(appid, name, playtime, null, null, null, null, "https://store.steampowered.com/app/" + appid + "/");
	}

	private static List<Long> sortedIds(SortBy sortBy, GameSummary... games) {
		return List.of(games).stream().sorted(sortBy.comparator()).map(GameSummary::appid).toList();
	}

	@Test
	void parse_withNullOrBlank_shouldReturnDefault() {
		assertThat(SortBy.parse(null)).isEqualTo(SortBy.DEFAULT);
		assertThat(SortBy.parse("  ")).isEqualTo(SortBy.DEFAULT);
	}

	@Test
	void parse_withMixedCaseAndPadding_shouldNormalize() {
		assertThat(SortBy.parse(" name_Desc ")).isEqualTo(SortBy.NAME_DESC);
	}

	@Test
	void parse_withUnknownValue_shouldThrowInvalidArgument() {
		assertThatThrownBy(() -> SortBy.parse("RANDOM")).isInstanceOfSatisfying(SteamException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT))
			.hasMessageContaining("NAME_ASC");
	}

	@Test
	void comparator_nameAsc_shouldIgnoreCaseAndSortNullNameFirst() {
		assertThat(sortedIds(SortBy.NAME_ASC, game(1, "beta", 1), game(2, "Alpha", 1), game(3, null, 1)))
			.containsExactly(3L, 2L, 1L);
	}

	@Test
	void comparator_nameDesc_shouldReverseNameOrder() {
		assertThat(sortedIds(SortBy.NAME_DESC, game(1, "beta", 1), game(2, "Alpha", 1))).containsExactly(1L, 2L);
	}

	@Test
	void comparator_playtimeDesc_shouldTreatNullAsZeroAndBreakTiesByNameThenAppid() {
		assertThat(sortedIds(SortBy.PLAYTIME_DESC, game(1, "b", null), game(2, "a", 0), game(3, "a", 0),
				game(4, "z", 90)))
			.containsExactly(4L, 2L, 3L, 1L);
	}

	@Test
	void comparator_playtimeAsc_shouldPutLeastPlayedFirst() {
		assertThat(sortedIds(SortBy.PLAYTIME_ASC, game(1, "a", 90), game(2, "b", null))).containsExactly(2L, 1L);
	}

}
