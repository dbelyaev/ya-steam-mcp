package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameSchemaResponse(Game game) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Game(String gameName, AvailableGameStats availableGameStats) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AvailableGameStats(List<Achievement> achievements) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Achievement(String name, String displayName, String description, Integer hidden) {
	}

}
