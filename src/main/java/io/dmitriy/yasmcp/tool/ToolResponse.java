package io.dmitriy.yasmcp.tool;

import java.time.Instant;
import java.util.List;

public record ToolResponse<T>(T data, Meta meta) {

	public static final String SOURCE = "Steam Web API";

	public record Meta(String source, Instant fetchedAt, boolean cached, boolean partial, List<String> warnings) {

		public static Meta of(Instant fetchedAt, boolean cached, boolean partial, List<String> warnings) {
			return new Meta(SOURCE, fetchedAt, cached, partial, List.copyOf(warnings));
		}

	}

}
