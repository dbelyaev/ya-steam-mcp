package io.dmitriy.yasteammcp.client;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.steamdto.NewsResponse;
import io.dmitriy.yasteammcp.steamdto.OwnedGamesResponse;
import io.dmitriy.yasteammcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasteammcp.steamdto.RecentlyPlayedResponse;

/**
 * Thin adapter over the public Steam Web API. Every failure surfaces as
 * {@link io.dmitriy.yasteammcp.model.SteamException} with a fixed generic message.
 * Callers pass already validated arguments.
 */
public interface SteamApiClient {

	OwnedGamesResponse getOwnedGames();

	RecentlyPlayedResponse getRecentlyPlayedGames(int count);

	PlayerAchievementsResponse getPlayerAchievements(long appid, String language);

	GameSchemaResponse getSchemaForGame(long appid, String language);

	/** @param maxLength Steam {@code maxlength}; {@code null} omits the parameter */
	NewsResponse getNewsForApp(long appid, int count, @Nullable Integer maxLength);

}
