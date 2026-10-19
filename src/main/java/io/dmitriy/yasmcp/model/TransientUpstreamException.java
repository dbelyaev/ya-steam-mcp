package io.dmitriy.yasmcp.model;

/**
 * Upstream failure worth retrying (I/O error, 5xx). The only exception type
 * {@code @Retryable} on the Steam client reacts to; 429, auth and parse errors are not retried.
 */
public class TransientUpstreamException extends SteamException {

	public TransientUpstreamException() {
		super(ErrorCode.UPSTREAM_UNAVAILABLE, "Steam API is temporarily unavailable");
	}

}
