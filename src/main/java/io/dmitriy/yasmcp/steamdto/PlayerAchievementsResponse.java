package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerAchievementsResponse(Stats playerstats) {

	/** {@code success=false} with an {@code error} text is how Steam reports "no stats" or private data. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Stats(
			String steamID,
			String gameName,
			Boolean success,
			String error,
			List<Achievement> achievements) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Achievement(
			String apiname,
			Integer achieved,
			Long unlocktime,
			String name,
			String description) {
	}

}
