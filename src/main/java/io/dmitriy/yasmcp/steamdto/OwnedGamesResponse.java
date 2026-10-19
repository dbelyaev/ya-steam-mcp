package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnedGamesResponse(@Nullable Body response) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(@JsonProperty("game_count") @Nullable Integer gameCount, @Nullable List<Game> games) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Game(
			long appid,
			@Nullable String name,
			@JsonProperty("playtime_forever") @Nullable Integer playtimeForever,
			@JsonProperty("playtime_windows_forever") @Nullable Integer playtimeWindowsForever,
			@JsonProperty("playtime_mac_forever") @Nullable Integer playtimeMacForever,
			@JsonProperty("playtime_linux_forever") @Nullable Integer playtimeLinuxForever,
			@JsonProperty("img_icon_url") @Nullable String imgIconUrl,
			@JsonProperty("rtime_last_played") @Nullable Long rtimeLastPlayed) {
	}

}
