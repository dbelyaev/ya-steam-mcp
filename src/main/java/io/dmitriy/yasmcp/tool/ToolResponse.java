package io.dmitriy.yasmcp.tool;

import java.time.Instant;
import java.util.List;

import io.dmitriy.yasmcp.model.Result;

public record ToolResponse<T>(T data, Meta meta) {

	public static final String SOURCE = "Steam Web API";

	public static <T> ToolResponse<T> of(Result<T> result, Instant fetchedAt) {
		return new ToolResponse<>(result.data(), Meta.of(fetchedAt, false, result.partial(), result.warnings()));
	}

	public ToolResponse<T> withCached(boolean cached) {
		return new ToolResponse<>(data, new Meta(meta.source(), meta.fetchedAt(), cached, meta.partial(), meta.warnings()));
	}

	public record Meta(String source, Instant fetchedAt, boolean cached, boolean partial, List<String> warnings) {

		public static Meta of(Instant fetchedAt, boolean cached, boolean partial, List<String> warnings) {
			return new Meta(SOURCE, fetchedAt, cached, partial, List.copyOf(warnings));
		}

	}

}
