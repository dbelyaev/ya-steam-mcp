package io.dmitriy.yasteammcp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.config.SteamProperties;
import io.dmitriy.yasteammcp.model.Achievement;
import io.dmitriy.yasteammcp.model.AchievementList;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.Result;
import io.dmitriy.yasteammcp.model.SteamException;
import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.steamdto.PlayerAchievementsResponse;
import io.dmitriy.yasteammcp.support.AppIdValidator;
import io.dmitriy.yasteammcp.support.SteamLanguage;

/**
 * Per-player achievements, enriched from the game schema. Missing or hidden data is never
 * reported as "0 of 0": it becomes an error or {@code available=false}.
 */
@Service
public class AchievementService {

	static final int DEFAULT_LIMIT = 100;

	static final int MAX_LIMIT = 250;

	private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

	private final SteamApiClient client;

	private final SteamProperties.Api api;

	public AchievementService(SteamApiClient client, SteamProperties properties) {
		this.client = client;
		this.api = properties.api();
	}

	public Result<AchievementList> getAchievements(@Nullable Long appid, @Nullable String language,
			@Nullable Boolean includeLocked, @Nullable Integer limit) {
		long id = AppIdValidator.requireValid(appid);
		String lang = SteamLanguage.resolve(language, api.defaultLanguage());
		boolean withLocked = includeLocked == null || includeLocked;
		int max = limit == null ? DEFAULT_LIMIT : limit;
		if (max < 1 || max > MAX_LIMIT) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT, "limit must be between 1 and " + MAX_LIMIT);
		}

		PlayerAchievementsResponse.Stats stats = client.getPlayerAchievements(id, lang).playerstats();
		if (stats == null || Boolean.FALSE.equals(stats.success())) {
			throw unavailable(stats == null ? null : stats.error());
		}
		List<PlayerAchievementsResponse.Achievement> player = stats.achievements();
		if (player == null || player.isEmpty()) {
			return new Result<>(new AchievementList(id, false, 0, 0, 0, false, List.of()), false,
					List.of("Steam returned no achievement list for this game; it may not support achievements or "
							+ "the data may be hidden, so zero is not implied"));
		}

		List<String> warnings = new ArrayList<>();
		Map<String, GameSchemaResponse.Achievement> schema = schemaByName(id, lang, warnings);

		List<Achievement> all = player.stream()
			.map(p -> AchievementMapper.toAchievement(p, p.apiname() == null ? null : schema.get(p.apiname())))
			.filter(java.util.Objects::nonNull)
			.toList();
		List<Achievement> filtered = all.stream().filter(a -> withLocked || a.achieved()).toList();
		List<Achievement> page = filtered.size() > max ? filtered.subList(0, max) : filtered;
		int unlocked = (int) all.stream().filter(Achievement::achieved).count();

		AchievementList data = new AchievementList(id, true, all.size(), unlocked, page.size(), filtered.size() > max,
				page);
		return new Result<>(data, !warnings.isEmpty(), warnings);
	}

	/** Schema failure degrades to a partial result: the player's own data is still valid. */
	private Map<String, GameSchemaResponse.Achievement> schemaByName(long appid, String language, List<String> warnings) {
		try {
			GameSchemaResponse.Game game = client.getSchemaForGame(appid, language).game();
			var stats = game == null ? null : game.availableGameStats();
			List<GameSchemaResponse.Achievement> list = stats == null ? null : stats.achievements();
			Map<String, GameSchemaResponse.Achievement> byName = new HashMap<>();
			if (list != null) {
				list.stream().filter(a -> a.name() != null).forEach(a -> byName.put(a.name(), a));
			}
			if (byName.isEmpty()) {
				warnings.add("Achievement schema was empty; hidden flags and localized texts may be missing");
			}
			return byName;
		}
		catch (SteamException ex) {
			log.warn("Achievement schema unavailable for appid {}: {}", appid, ex.code());
			warnings.add("Achievement schema is unavailable; names, descriptions and hidden flags may be missing");
			return Map.of();
		}
	}

	private static SteamException unavailable(@Nullable String steamError) {
		String error = steamError == null ? "" : steamError.toLowerCase(Locale.ROOT);
		if (error.contains("not public") || error.contains("private") || error.contains("profile")) {
			return new SteamException(ErrorCode.DATA_NOT_ACCESSIBLE,
					"Achievements are not accessible; the profile or game details may be private");
		}
		return new SteamException(ErrorCode.ACHIEVEMENTS_UNAVAILABLE, "Achievements are not available for this game");
	}

}
