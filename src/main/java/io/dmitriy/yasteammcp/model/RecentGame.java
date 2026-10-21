package io.dmitriy.yasteammcp.model;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.steamdto.RecentlyPlayedResponse;

public record RecentGame(
		long appid,
		@Nullable String name,
		@Nullable Integer playtimeForeverMinutes,
		@Nullable Integer playtimeRecentMinutes,
		String storeUrl) {

	public static RecentGame from(RecentlyPlayedResponse.Game game) {
		return new RecentGame(game.appid(), game.name(), game.playtimeForever(), game.playtime2weeks(),
				StoreUrls.app(game.appid()));
	}

}
