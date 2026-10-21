package io.dmitriy.yasteammcp.model;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.steamdto.OwnedGamesResponse;

/** Public view of one owned game. All playtimes are minutes. Missing upstream fields stay null. */
public record GameSummary(
		long appid,
		@Nullable String name,
		@Nullable Integer playtimeForeverMinutes,
		@Nullable Integer playtimeWindowsForeverMinutes,
		@Nullable Integer playtimeMacForeverMinutes,
		@Nullable Integer playtimeLinuxForeverMinutes,
		@Nullable String iconUrl,
		String storeUrl) {

	/** Steam only returns an icon hash; building an image URL from it is undocumented, so iconUrl stays null. */
	public static GameSummary from(OwnedGamesResponse.Game game) {
		return new GameSummary(game.appid(), game.name(), game.playtimeForever(), game.playtimeWindowsForever(),
				game.playtimeMacForever(), game.playtimeLinuxForever(), null, StoreUrls.app(game.appid()));
	}

	/** Missing playtime counts as zero for sorting and the never-played filter only. */
	public int playtimeOrZero() {
		return playtimeForeverMinutes == null ? 0 : playtimeForeverMinutes;
	}

}
