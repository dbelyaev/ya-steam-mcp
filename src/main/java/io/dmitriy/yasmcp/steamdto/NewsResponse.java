package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsResponse(AppNews appnews) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AppNews(Long appid, List<Item> newsitems) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(String gid, String title, String url, String author, String contents, Long date) {
	}

}
