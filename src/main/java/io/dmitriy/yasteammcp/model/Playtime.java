package io.dmitriy.yasteammcp.model;

import org.jspecify.annotations.Nullable;

public record Playtime(
		long appid,
		@Nullable String name,
		@Nullable Integer playtimeForeverMinutes,
		@Nullable Integer playtimeWindowsForeverMinutes,
		@Nullable Integer playtimeMacForeverMinutes,
		@Nullable Integer playtimeLinuxForeverMinutes,
		String source) {

	public static final String SOURCE_ENDPOINT = "GetOwnedGames";

	public static Playtime from(GameSummary game) {
		return new Playtime(game.appid(), game.name(), game.playtimeForeverMinutes(),
				game.playtimeWindowsForeverMinutes(), game.playtimeMacForeverMinutes(),
				game.playtimeLinuxForeverMinutes(), SOURCE_ENDPOINT);
	}

}
