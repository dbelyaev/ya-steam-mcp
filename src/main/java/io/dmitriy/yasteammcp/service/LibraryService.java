package io.dmitriy.yasteammcp.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.config.SteamProperties;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.GameSummary;
import io.dmitriy.yasteammcp.model.LibraryPage;
import io.dmitriy.yasteammcp.model.Playtime;
import io.dmitriy.yasteammcp.model.Result;
import io.dmitriy.yasteammcp.model.SteamException;
import io.dmitriy.yasteammcp.model.VisibilityStatus;
import io.dmitriy.yasteammcp.steamdto.OwnedGamesResponse;
import io.dmitriy.yasteammcp.support.AppIdValidator;

/**
 * Owned-games capability: one upstream fetch per call, then filter/sort/paginate locally.
 * An empty upstream list is never read as an empty or private library.
 */
@Service
public class LibraryService {

	private final SteamApiClient client;

	private final SteamProperties.Api api;

	public LibraryService(SteamApiClient client, SteamProperties properties) {
		this.client = client;
		this.api = properties.api();
	}

	public Result<LibraryPage> getLibrary(@Nullable String query, @Nullable String sortBy, @Nullable Integer offset,
			@Nullable Integer limit, @Nullable Boolean includeNeverPlayed) {
		LibraryQuery q = LibraryQuery.of(query, sortBy, offset, limit, includeNeverPlayed);
		String steamId = api.requireSteamId();
		List<GameSummary> owned = ownedGames();

		List<GameSummary> filtered = owned.stream()
			.filter(g -> q.includeNeverPlayed() || g.playtimeOrZero() > 0)
			.filter(g -> q.nameFilter() == null || (g.name() != null && g.name().toLowerCase(Locale.ROOT).contains(q.nameFilter())))
			.sorted(q.sortBy().comparator())
			.toList();

		int from = Math.min(q.offset(), filtered.size());
		int to = (int) Math.min((long) from + q.limit(), filtered.size());
		VisibilityStatus visibility = owned.isEmpty() ? VisibilityStatus.UNKNOWN : VisibilityStatus.VISIBLE;
		return Result.complete(new LibraryPage(steamId, filtered.size(), q.offset(), q.limit(),
				filtered.subList(from, to), visibility));
	}

	public Result<Playtime> getPlaytime(@Nullable Long appid) {
		long id = AppIdValidator.requireValid(appid);
		api.requireSteamId();
		GameSummary game = findOwned(id).orElseThrow(() -> new SteamException(ErrorCode.GAME_NOT_VISIBLE_OR_NOT_OWNED,
				"The game is not in the visible library; it may be unowned or hidden by privacy settings"));
		return Result.complete(Playtime.from(game));
	}

	/** Lookup shared with other capabilities (game details). */
	public Optional<GameSummary> findOwned(long appid) {
		return ownedGames().stream().filter(g -> g.appid() == appid).findFirst();
	}

	private List<GameSummary> ownedGames() {
		OwnedGamesResponse.Body body = client.getOwnedGames().response();
		List<OwnedGamesResponse.Game> games = body == null ? null : body.games();
		return games == null ? List.of() : games.stream().map(GameSummary::from).toList();
	}

}
