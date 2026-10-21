package io.dmitriy.yasteammcp.model;

import java.util.List;

/** {@code total} counts games after filtering and before pagination. */
public record LibraryPage(
		String steamId,
		int total,
		int offset,
		int limit,
		List<GameSummary> games,
		VisibilityStatus visibilityStatus) {
}
