package io.dmitriy.yasteammcp.service;

import java.util.Comparator;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.GameSummary;
import io.dmitriy.yasteammcp.model.SteamException;

/** Library ordering. Ties break by name then appid so pages are stable. */
public enum SortBy {

	NAME_ASC(byName()),
	NAME_DESC(byName().reversed()),
	PLAYTIME_ASC(Comparator.comparingInt(GameSummary::playtimeOrZero)),
	PLAYTIME_DESC(Comparator.comparingInt(GameSummary::playtimeOrZero).reversed());

	public static final SortBy DEFAULT = PLAYTIME_DESC;

	private final Comparator<GameSummary> comparator;

	SortBy(Comparator<GameSummary> primary) {
		this.comparator = primary.thenComparing(byName()).thenComparingLong(GameSummary::appid);
	}

	public Comparator<GameSummary> comparator() {
		return comparator;
	}

	public static SortBy parse(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return DEFAULT;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException _) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT,
					"sortBy must be one of NAME_ASC, NAME_DESC, PLAYTIME_ASC, PLAYTIME_DESC");
		}
	}

	private static Comparator<GameSummary> byName() {
		return Comparator.comparing(g -> g.name() == null ? "" : g.name(), String.CASE_INSENSITIVE_ORDER);
	}

}
