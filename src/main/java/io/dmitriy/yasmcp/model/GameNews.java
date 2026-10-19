package io.dmitriy.yasmcp.model;

import java.util.List;

public record GameNews(long appid, List<NewsItem> items) {
}
