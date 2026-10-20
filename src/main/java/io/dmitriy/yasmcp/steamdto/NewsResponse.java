package io.dmitriy.yasmcp.steamdto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsResponse(@Nullable AppNews appnews) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AppNews(@Nullable Long appid, @Nullable List<Item> newsitems) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(@Nullable String gid, @Nullable String title, @Nullable String url, @Nullable String author, @Nullable String contents, @Nullable Long date) {
	}

}
