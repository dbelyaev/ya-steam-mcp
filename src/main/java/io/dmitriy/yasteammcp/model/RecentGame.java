package io.dmitriy.yasteammcp.model;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.steamdto.RecentlyPlayedResponse;
import io.dmitriy.yasteammcp.support.TextSanitizer;

public record RecentGame(
		long appid,
		@Nullable String name,
		@Nullable Integer playtimeForeverMinutes,
		@Nullable Integer playtimeRecentMinutes,
		String storeUrl) {

	public static RecentGame from(RecentlyPlayedResponse.Game game) {
		return new RecentGame(game.appid(), TextSanitizer.plainText(game.name(), TextSanitizer.MAX_NAME_CHARS), game.playtimeForever(), game.playtime2weeks(),
				StoreUrls.app(game.appid()));
	}

}
