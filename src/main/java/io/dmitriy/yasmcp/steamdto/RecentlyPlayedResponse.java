package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecentlyPlayedResponse(Body response) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(@JsonProperty("total_count") Integer totalCount, List<Game> games) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Game(
			long appid,
			String name,
			@JsonProperty("playtime_2weeks") Integer playtime2weeks,
			@JsonProperty("playtime_forever") Integer playtimeForever,
			@JsonProperty("img_icon_url") String imgIconUrl) {
	}

}
