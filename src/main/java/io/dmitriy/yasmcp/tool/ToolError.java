package io.dmitriy.yasmcp.tool;

import io.dmitriy.yasmcp.model.ErrorCode;

public record ToolError(ErrorCode code, String message, boolean retryable) {

	public static ToolError of(ErrorCode code, String message) {
		return new ToolError(code, message, code.retryable());
	}

}
