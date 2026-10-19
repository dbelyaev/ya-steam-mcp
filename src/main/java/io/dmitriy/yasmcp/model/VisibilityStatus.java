package io.dmitriy.yasmcp.model;

/**
 * What the library response proves about privacy. An empty Steam response never proves a
 * private or empty library, so it stays {@code UNKNOWN}.
 */
public enum VisibilityStatus {

	/** Steam returned at least one game: the library is visible to the API caller. */
	VISIBLE,

	/** Empty or missing games list: private, empty, or hidden details are indistinguishable. */
	UNKNOWN

}
