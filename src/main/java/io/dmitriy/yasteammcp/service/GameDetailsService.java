package io.dmitriy.yasteammcp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.config.SteamProperties;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.GameDetails;
import io.dmitriy.yasteammcp.model.GameSummary;
import io.dmitriy.yasteammcp.model.Result;
import io.dmitriy.yasteammcp.model.StoreUrls;
import io.dmitriy.yasteammcp.model.SteamException;
import io.dmitriy.yasteammcp.steamdto.GameSchemaResponse;
import io.dmitriy.yasteammcp.support.AppIdValidator;
import io.dmitriy.yasteammcp.support.SteamLanguage;

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
		SchemaLookup schema = lookUpSchema(id, lang);
		failIfNothingAnswered(owned, schema);
		return assemble(id, owned.orElse(null), schema);
	}

	/** The schema is optional enrichment: a failure is kept, logged, and reported as a warning. */
	private SchemaLookup lookUpSchema(long appid, String language) {
		try {
			return new SchemaLookup(client.getSchemaForGame(appid, language).game(), null);
		}
		catch (SteamException ex) {
			log.warn("Game schema unavailable for appid {}: {}", appid, ex.code());
			return new SchemaLookup(null, ex);
		}
	}

	private static void failIfNothingAnswered(Optional<GameSummary> owned, SchemaLookup schema) {
		if (owned.isPresent()) {
			return;
		}
		if (schema.failure() != null) {
			// nothing else answered, so a transient schema failure must stay visible (and retryable)
			throw schema.failure();
		}
		if (!schema.hasData()) {
			throw new SteamException(ErrorCode.GAME_DETAILS_UNAVAILABLE,
					"No details are available for this game from the library or the game schema");
		}
	}

	private static Result<GameDetails> assemble(long id, @Nullable GameSummary game, SchemaLookup schema) {
		List<String> warnings = new ArrayList<>();
		List<String> sources = new ArrayList<>();
		if (schema.failure() != null) {
			warnings.add("The game schema is unavailable; achievement count and schema name are missing");
		}
		if (game != null) {
			sources.add("GetOwnedGames");
		}
		else {
			warnings.add("The game is not in the visible library; playtime and ownership are unknown");
		}
		if (schema.hasData()) {
			sources.add("GetSchemaForGame");
		}
		String name = game != null && game.name() != null ? game.name() : schema.name();
		GameDetails details = new GameDetails(id, name, StoreUrls.app(id), null, game != null,
				game == null ? null : game.playtimeForeverMinutes(), schema.achievementCount(), null, null, null,
				sources);
		return new Result<>(details, !warnings.isEmpty(), warnings);
	}

	/** Outcome of the schema call: the game data (possibly empty) or the failure that prevented it. */
	private record SchemaLookup(GameSchemaResponse.@Nullable Game game, @Nullable SteamException failure) {

		@Nullable String name() {
			return game == null ? null : game.gameName();
		}

		boolean hasData() {
			return name() != null || (game != null && game.availableGameStats() != null);
		}

		@Nullable Integer achievementCount() {
			var stats = game == null ? null : game.availableGameStats();
			var achievements = stats == null ? null : stats.achievements();
			return achievements == null ? null : achievements.size();
		}

	}

}
