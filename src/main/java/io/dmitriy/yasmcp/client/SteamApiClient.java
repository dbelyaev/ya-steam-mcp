package io.dmitriy.yasmcp.client;

import io.dmitriy.yasmcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasmcp.steamdto.NewsResponse;
import io.dmitriy.yasmcp.steamdto.OwnedGamesResponse;
import io.dmitriy.yasmcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasmcp.steamdto.RecentlyPlayedResponse;

/**
 * Thin adapter over the public Steam Web API. Every failure surfaces as
 * {@link io.dmitriy.yasmcp.model.SteamException} with a fixed generic message.
 * Callers pass already validated arguments.
 */
public interface SteamApiClient {

	OwnedGamesResponse getOwnedGames();

	RecentlyPlayedResponse getRecentlyPlayedGames(int count);

	PlayerAchievementsResponse getPlayerAchievements(long appid, String language);

	GameSchemaResponse getSchemaForGame(long appid, String language);

	/** @param maxLength Steam {@code maxlength}; {@code null} omits the parameter */
	NewsResponse getNewsForApp(long appid, int count, Integer maxLength);

}
