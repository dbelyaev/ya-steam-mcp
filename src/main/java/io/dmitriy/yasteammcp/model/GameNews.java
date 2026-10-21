package io.dmitriy.yasteammcp.model;

import java.util.List;

public record GameNews(long appid, List<NewsItem> items) {
}
