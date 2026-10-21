package io.dmitriy.yasteammcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameSchemaResponse(@Nullable Game game) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Game(@Nullable String gameName, @Nullable AvailableGameStats availableGameStats) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AvailableGameStats(@Nullable List<Achievement> achievements) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Achievement(@Nullable String name, @Nullable String displayName, @Nullable String description, @Nullable Integer hidden) {
	}

}
