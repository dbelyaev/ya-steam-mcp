package io.dmitriy.yasteammcp.service;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.config.SteamProperties;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.RecentGame;
import io.dmitriy.yasteammcp.model.RecentGames;
import io.dmitriy.yasteammcp.model.Result;
import io.dmitriy.yasteammcp.model.SteamException;
import io.dmitriy.yasteammcp.steamdto.RecentlyPlayedResponse;

@Service
public class RecentGamesService {

	static final int DEFAULT_LIMIT = 20;

	static final int MAX_LIMIT = 100;

	private final SteamApiClient client;

	private final SteamProperties.Api api;

	public RecentGamesService(SteamApiClient client, SteamProperties properties) {
		this.client = client;
		this.api = properties.api();
	}

	public Result<RecentGames> getRecentGames(@Nullable Integer limit) {
		int count = limit == null ? DEFAULT_LIMIT : limit;
		if (count < 1 || count > MAX_LIMIT) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT, "limit must be between 1 and " + MAX_LIMIT);
		}
		String steamId = api.requireSteamId();
		RecentlyPlayedResponse.Body body = client.getRecentlyPlayedGames(count).response();
		List<RecentlyPlayedResponse.Game> games = body == null ? null : body.games();
		List<RecentGame> mapped = games == null ? List.of() : games.stream().map(RecentGame::from).limit(count).toList();
		return Result.complete(new RecentGames(steamId, RecentGames.WINDOW, mapped));
	}

}
