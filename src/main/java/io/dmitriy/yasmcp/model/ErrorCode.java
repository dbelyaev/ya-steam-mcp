package io.dmitriy.yasmcp.model;

public enum ErrorCode {

	INVALID_ARGUMENT(false),
	CONFIGURATION_MISSING(false),
	UPSTREAM_AUTH_FAILED(false),
	DATA_NOT_ACCESSIBLE(false),
	GAME_NOT_VISIBLE_OR_NOT_OWNED(false),
	GAME_DETAILS_UNAVAILABLE(false),
	ACHIEVEMENTS_UNAVAILABLE(false),
	UPSTREAM_RATE_LIMITED(true),
	UPSTREAM_UNAVAILABLE(true),
	UPSTREAM_BAD_RESPONSE(false),
	INTERNAL_ERROR(false);

	private final boolean retryable;

	ErrorCode(boolean retryable) {
		this.retryable = retryable;
	}

	public boolean retryable() {
		return retryable;
	}

}
