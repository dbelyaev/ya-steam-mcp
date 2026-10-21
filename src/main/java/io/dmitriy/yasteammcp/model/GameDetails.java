package io.dmitriy.yasteammcp.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Only what official sources provide. {@code description}, {@code genres} and {@code releaseDate}
 * are always null until Store enrichment exists (disabled in the MVP).
 */
public record GameDetails(
		long appid,
		@Nullable String name,
		String storeUrl,
		@Nullable String iconUrl,
		boolean ownedOrVisibleInLibrary,
		@Nullable Integer playtimeForeverMinutes,
		@Nullable Integer achievementCount,
		@Nullable String description,
		@Nullable List<String> genres,
		@Nullable String releaseDate,
		List<String> sources) {
}
