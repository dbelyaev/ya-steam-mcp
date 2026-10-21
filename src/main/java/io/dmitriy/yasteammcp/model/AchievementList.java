package io.dmitriy.yasteammcp.model;

import java.util.List;

/**
 * {@code available=false} means Steam gave no achievement list for this game: that is not
 * the same as zero achievements, so counts are then not claims.
 */
public record AchievementList(
		long appid,
		boolean available,
		int total,
		int unlocked,
		int returned,
		boolean truncated,
		List<Achievement> achievements) {
}
