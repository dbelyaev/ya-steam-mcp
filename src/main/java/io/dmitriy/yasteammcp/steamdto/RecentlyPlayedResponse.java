package io.dmitriy.yasteammcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecentlyPlayedResponse(@Nullable Body response) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(@JsonProperty("total_count") @Nullable Integer totalCount, @Nullable List<Game> games) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Game(
			long appid,
			@Nullable String name,
			@JsonProperty("playtime_2weeks") @Nullable Integer playtime2weeks,
			@JsonProperty("playtime_forever") @Nullable Integer playtimeForever,
			@JsonProperty("img_icon_url") @Nullable String imgIconUrl) {
	}

}
