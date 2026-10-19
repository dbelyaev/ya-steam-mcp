package io.dmitriy.yasmcp.model;

/**
 * Expected failure with a public error code. The message is shown to MCP clients,
 * so it must be fixed and generic: no URLs, keys or upstream bodies.
 */
public class SteamException extends RuntimeException {

	private final ErrorCode code;

	public SteamException(ErrorCode code, String message) {
		super(message, null, false, false);
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}

	public boolean retryable() {
		return code.retryable();
	}

}
