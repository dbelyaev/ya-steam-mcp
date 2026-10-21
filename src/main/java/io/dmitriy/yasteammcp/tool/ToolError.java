package io.dmitriy.yasteammcp.tool;

import io.dmitriy.yasteammcp.model.ErrorCode;

public record ToolError(ErrorCode code, String message, boolean retryable) {

	public static ToolError of(ErrorCode code, String message) {
		return new ToolError(code, message, code.retryable());
	}

}
