package io.dmitriy.yasteammcp.service;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import io.dmitriy.yasteammcp.client.SteamApiClient;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.GameNews;
import io.dmitriy.yasteammcp.model.NewsItem;
import io.dmitriy.yasteammcp.model.Result;
import io.dmitriy.yasteammcp.model.SteamException;
import io.dmitriy.yasteammcp.steamdto.NewsResponse;
import io.dmitriy.yasteammcp.support.AppIdValidator;
import io.dmitriy.yasteammcp.support.NewsSanitizer;

/** Game news. Needs no API key. All text is untrusted and returned as sanitized plain text. */
@Service
public class NewsService {

	static final int DEFAULT_LIMIT = 5;

	static final int MAX_LIMIT = 20;

	static final int DEFAULT_CHARS = 1200;

	static final int MAX_CHARS = 4000;

	private static final int MAX_TITLE_CHARS = 300;

	private static final int MAX_AUTHOR_CHARS = 100;

	/** Raw markup is longer than its text, so Steam is asked for more than the preview needs; capped. */
	private static final int RAW_FACTOR = 3;

	private static final int MAX_RAW_CHARS = 12_000;

	private final SteamApiClient client;

	public NewsService(SteamApiClient client) {
		this.client = client;
	}

	public Result<GameNews> getNews(@Nullable Long appid, @Nullable Integer limit, @Nullable Integer maxContentChars) {
		long id = AppIdValidator.requireValid(appid);
		int count = limit == null ? DEFAULT_LIMIT : limit;
		if (count < 1 || count > MAX_LIMIT) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT, "limit must be between 1 and " + MAX_LIMIT);
		}
		int chars = maxContentChars == null ? DEFAULT_CHARS : maxContentChars;
		if (chars < 0 || chars > MAX_CHARS) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT, "maxContentChars must be between 0 and " + MAX_CHARS);
		}

		// Steam treats maxlength 0 as "full content", so "no body" asks for the smallest value instead.
		int steamMaxLength = chars == 0 ? 1 : Math.min(chars * RAW_FACTOR, MAX_RAW_CHARS);
		NewsResponse.AppNews news = client.getNewsForApp(id, count, steamMaxLength).appnews();
		List<NewsResponse.Item> items = news == null ? null : news.newsitems();
		List<NewsItem> mapped = items == null ? List.of()
				: items.stream().limit(count).map(item -> toNewsItem(item, chars)).toList();
		return Result.complete(new GameNews(id, mapped));
	}

	private static NewsItem toNewsItem(NewsResponse.Item item, int chars) {
		Long date = item.date();
		Instant publishedAt = date == null || date <= 0 ? null : Instant.ofEpochSecond(date);
		return new NewsItem(item.gid(), blankToNull(NewsSanitizer.toPlainText(item.title(), MAX_TITLE_CHARS)),
				NewsSanitizer.safeHttpsUrl(item.url()), publishedAt,
				blankToNull(NewsSanitizer.toPlainText(item.author(), MAX_AUTHOR_CHARS)),
				chars == 0 ? null : blankToNull(NewsSanitizer.toPlainText(item.contents(), chars)));
	}

	private static @Nullable String blankToNull(String text) {
		return text.isBlank() ? null : text;
	}

}
