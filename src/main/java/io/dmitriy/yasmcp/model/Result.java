package io.dmitriy.yasmcp.model;

import java.util.List;

/** Service outcome: public data plus provenance flags the tool layer turns into response meta. */
public record Result<T>(T data, boolean partial, List<String> warnings) {

	public static <T> Result<T> complete(T data) {
		return new Result<>(data, false, List.of());
	}

}
