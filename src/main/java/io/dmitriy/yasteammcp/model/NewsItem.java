package io.dmitriy.yasteammcp.model;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** All text fields are untrusted third-party content reduced to plain text. */
public record NewsItem(
		@Nullable String id,
		@Nullable String title,
		@Nullable String url,
		@Nullable Instant publishedAt,
		@Nullable String author,
		@Nullable String contentPreview) {
}
