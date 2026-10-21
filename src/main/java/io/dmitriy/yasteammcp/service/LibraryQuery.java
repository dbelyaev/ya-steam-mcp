package io.dmitriy.yasteammcp.service;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;

/** Validated library request. Defaults live here, not in the tool, so clients cannot override them. */
record LibraryQuery(@Nullable String nameFilter, SortBy sortBy, int offset, int limit, boolean includeNeverPlayed) {

	static final int DEFAULT_LIMIT = 20;

	static final int MAX_LIMIT = 100;

	static final int MAX_OFFSET = 100_000;

	static final int MAX_QUERY_LENGTH = 100;

	static LibraryQuery of(@Nullable String query, @Nullable String sortBy, @Nullable Integer offset,
			@Nullable Integer limit, @Nullable Boolean includeNeverPlayed) {
		String filter = query == null || query.isBlank() ? null : query.trim();
		if (filter != null && filter.length() > MAX_QUERY_LENGTH) {
			throw invalid("query must be at most " + MAX_QUERY_LENGTH + " characters");
		}
		int off = offset == null ? 0 : offset;
		if (off < 0 || off > MAX_OFFSET) {
			throw invalid("offset must be between 0 and " + MAX_OFFSET);
		}
		int lim = limit == null ? DEFAULT_LIMIT : limit;
		if (lim < 1 || lim > MAX_LIMIT) {
			throw invalid("limit must be between 1 and " + MAX_LIMIT);
		}
		return new LibraryQuery(filter == null ? null : filter.toLowerCase(Locale.ROOT), SortBy.parse(sortBy), off, lim,
				includeNeverPlayed == null || includeNeverPlayed);
	}

	private static SteamException invalid(String message) {
		return new SteamException(ErrorCode.INVALID_ARGUMENT, message);
	}

}
