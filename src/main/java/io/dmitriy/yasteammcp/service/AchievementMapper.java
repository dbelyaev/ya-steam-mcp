package io.dmitriy.yasteammcp.service;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.model.Achievement;
import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasteammcp.support.TextSanitizer;

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
		String displayName = firstNonBlank(schema == null ? null : schema.displayName(), player.name(),
				TextSanitizer.MAX_NAME_CHARS);
		String description = firstNonBlank(schema == null ? null : schema.description(), player.description(),
				TextSanitizer.MAX_DESCRIPTION_CHARS);
		Boolean hidden = schema == null || schema.hidden() == null ? null : schema.hidden() == 1;
		return new Achievement(id, displayName, description, achieved, unlockTime, hidden);
	}

	/** Sanitized first usable text; a value that is blank after sanitizing falls through to the next. */
	private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second, int maxChars) {
		String clean = TextSanitizer.plainText(first, maxChars);
		return clean != null ? clean : TextSanitizer.plainText(second, maxChars);
	}

}
