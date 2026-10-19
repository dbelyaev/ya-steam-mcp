package io.dmitriy.yasmcp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.dmitriy.yasmcp.client.SteamApiClient;
import io.dmitriy.yasmcp.config.SteamProperties;
import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.GameDetails;
import io.dmitriy.yasmcp.model.GameSummary;
import io.dmitriy.yasmcp.model.Result;
import io.dmitriy.yasmcp.model.StoreUrls;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasmcp.support.AppIdValidator;
import io.dmitriy.yasmcp.support.SteamLanguage;

/**
 * Basic metadata from official sources only: the owned library and the game schema. Fields that
 * need the Store catalog (description, genres, release date) stay null; nothing is invented.
 * If neither source knows the game, the answer is {@code GAME_DETAILS_UNAVAILABLE} (D17).
 */
@Service
public class GameDetailsService {

	private static final Logger log = LoggerFactory.getLogger(GameDetailsService.class);

	private final LibraryService libraryService;

	private final SteamApiClient client;

	private final SteamProperties.Api api;

	public GameDetailsService(LibraryService libraryService, SteamApiClient client, SteamProperties properties) {
		this.libraryService = libraryService;
		this.client = client;
		this.api = properties.api();
	}

	public Result<GameDetails> getDetails(@Nullable Long appid, @Nullable String language) {
		long id = AppIdValidator.requireValid(appid);
		String lang = SteamLanguage.resolve(language, api.defaultLanguage());

		Optional<GameSummary> owned = libraryService.findOwned(id);
		List<String> warnings = new ArrayList<>();
		GameSchemaResponse.@Nullable Game schema = null;
		SteamException schemaFailure = null;
		try {
			schema = client.getSchemaForGame(id, lang).game();
		}
		catch (SteamException ex) {
			log.warn("Game schema unavailable for appid {}: {}", id, ex.code());
			schemaFailure = ex;
			warnings.add("The game schema is unavailable; achievement count and schema name are missing");
		}
		String schemaName = schema == null ? null : schema.gameName();
		var stats = schema == null ? null : schema.availableGameStats();
		boolean schemaHasData = schemaName != null || stats != null;

		if (owned.isEmpty() && schemaFailure != null) {
			// nothing else answered, so a transient schema failure must stay visible (and retryable)
			throw schemaFailure;
		}
		if (owned.isEmpty() && !schemaHasData) {
			throw new SteamException(ErrorCode.GAME_DETAILS_UNAVAILABLE,
					"No details are available for this game from the library or the game schema");
		}

		List<String> sources = new ArrayList<>();
		if (owned.isPresent()) {
			sources.add("GetOwnedGames");
		}
		else {
			warnings.add("The game is not in the visible library; playtime and ownership are unknown");
		}
		if (schemaHasData) {
			sources.add("GetSchemaForGame");
		}
		var achievements = stats == null ? null : stats.achievements();
		Integer achievementCount = achievements == null ? null : achievements.size();
		GameSummary game = owned.orElse(null);
		String name = game != null && game.name() != null ? game.name() : schemaName;
		boolean partial = owned.isEmpty() || !warnings.isEmpty();
		GameDetails details = new GameDetails(id, name, StoreUrls.app(id), null, owned.isPresent(),
				game == null ? null : game.playtimeForeverMinutes(), achievementCount, null, null, null, sources);
		return new Result<>(details, partial, warnings);
	}

}
