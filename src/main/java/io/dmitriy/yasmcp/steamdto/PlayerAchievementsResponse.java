package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerAchievementsResponse(@Nullable Stats playerstats) {

	/** {@code success=false} with an {@code error} text is how Steam reports "no stats" or private data. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Stats(
			@Nullable String steamID,
			@Nullable String gameName,
			@Nullable Boolean success,
			@Nullable String error,
			@Nullable List<Achievement> achievements) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Achievement(
			@Nullable String apiname,
			@Nullable Integer achieved,
			@Nullable Long unlocktime,
			@Nullable String name,
			@Nullable String description) {
	}

}
