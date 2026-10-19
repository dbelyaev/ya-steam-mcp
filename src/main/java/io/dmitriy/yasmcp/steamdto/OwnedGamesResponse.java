package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnedGamesResponse(Body response) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(@JsonProperty("game_count") Integer gameCount, List<Game> games) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Game(
			long appid,
			String name,
			@JsonProperty("playtime_forever") Integer playtimeForever,
			@JsonProperty("playtime_windows_forever") Integer playtimeWindowsForever,
			@JsonProperty("playtime_mac_forever") Integer playtimeMacForever,
			@JsonProperty("playtime_linux_forever") Integer playtimeLinuxForever,
			@JsonProperty("img_icon_url") String imgIconUrl,
			@JsonProperty("rtime_last_played") Long rtimeLastPlayed) {
	}

}
