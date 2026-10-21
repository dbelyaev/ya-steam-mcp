package io.dmitriy.yasteammcp.client;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Raw transport contract of the public Steam Web API: paths and parameters only.
 * Bodies come back as bytes with status and headers; {@link SteamApiClientImpl}
 * owns parsing, retry and error mapping. Registered as HTTP service group "steam".
 */
@HttpExchange(accept = "application/json")
public interface SteamWebApi {

	@GetExchange("/IPlayerService/GetOwnedGames/v1/")
	ResponseEntity<byte[]> getOwnedGames(@RequestParam("key") String key, @RequestParam("steamid") String steamId,
			@RequestParam("include_appinfo") boolean includeAppInfo,
			@RequestParam("include_played_free_games") boolean includePlayedFreeGames);

	@GetExchange("/IPlayerService/GetRecentlyPlayedGames/v1/")
	ResponseEntity<byte[]> getRecentlyPlayedGames(@RequestParam("key") String key,
			@RequestParam("steamid") String steamId, @RequestParam("count") int count);

	@GetExchange("/ISteamUserStats/GetPlayerAchievements/v1/")
	ResponseEntity<byte[]> getPlayerAchievements(@RequestParam("key") String key,
			@RequestParam("steamid") String steamId, @RequestParam("appid") long appid,
			@RequestParam("l") String language);

	@GetExchange("/ISteamUserStats/GetSchemaForGame/v2/")
	ResponseEntity<byte[]> getSchemaForGame(@RequestParam("key") String key, @RequestParam("appid") long appid,
			@RequestParam("l") String language);

	@GetExchange("/ISteamNews/GetNewsForApp/v2/")
	ResponseEntity<byte[]> getNewsForApp(@RequestParam("appid") long appid, @RequestParam("count") int count,
			@RequestParam(name = "maxlength", required = false) @Nullable Integer maxLength);

}
