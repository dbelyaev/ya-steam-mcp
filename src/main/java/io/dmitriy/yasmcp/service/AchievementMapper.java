package io.dmitriy.yasmcp.service;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasmcp.model.Achievement;
import io.dmitriy.yasmcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasmcp.steamdto.PlayerAchievementsResponse;

/** Joins a player's achievement with its schema entry (names, descriptions, hidden flag). */
final class AchievementMapper {

	private AchievementMapper() {
	}

	/** Schema wins for text because it is localized by the request language; player data is the fallback. */
	static @Nullable Achievement toAchievement(PlayerAchievementsResponse.Achievement player,
			GameSchemaResponse.@Nullable Achievement schema) {
		String id = player.apiname();
		if (id == null || id.isBlank()) {
			return null;
		}
		boolean achieved = player.achieved() != null && player.achieved() == 1;
		Long unlock = player.unlocktime();
		Instant unlockTime = achieved && unlock != null && unlock > 0 ? Instant.ofEpochSecond(unlock) : null;
		String displayName = firstNonBlank(schema == null ? null : schema.displayName(), player.name());
		String description = firstNonBlank(schema == null ? null : schema.description(), player.description());
		Boolean hidden = schema == null || schema.hidden() == null ? null : schema.hidden() == 1;
		return new Achievement(id, displayName, description, achieved, unlockTime, hidden);
	}

	private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		return second != null && !second.isBlank() ? second : null;
	}

}
