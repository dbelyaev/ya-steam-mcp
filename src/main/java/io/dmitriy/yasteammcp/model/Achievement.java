package io.dmitriy.yasteammcp.model;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

public record Achievement(
		String id,
		@Nullable String displayName,
		@Nullable String description,
		boolean achieved,
		@Nullable Instant unlockTime,
		@Nullable Boolean hidden) {
}
