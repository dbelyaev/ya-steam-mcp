package io.dmitriy.yasteammcp.support;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Shared cleanup for untrusted Steam text that reaches an agent. Strips control, zero-width and
 * bidi-override characters and caps the length; {@link NewsSanitizer} builds on the same primitives
 * for markup-heavy news. Markup is not interpreted here: a short label such as a game name keeps
 * its characters (CJK, emoji, punctuation) and is only made single-line and bounded.
 */
public final class TextSanitizer {

	/** Cap for names (games, achievements): longer than any real Steam title, short enough to be inert. */
	public static final int MAX_NAME_CHARS = 200;

	/** Cap for descriptions (achievements). */
	public static final int MAX_DESCRIPTION_CHARS = 500;

	private static final Pattern HIDDEN_CHARS = Pattern
		.compile("[\\p{Cc}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF&&[^\\n\\t]]");

	private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n\\t\\u2028\\u2029\\u0085]+");

	private static final Pattern BLANK_RUNS = Pattern.compile("[ \\t]+");

	private static final String ELLIPSIS = "\u2026";

	private TextSanitizer() {
	}

	/**
	 * Single-line inert text for a label or description.
	 *
	 * @return {@code null} when {@code raw} is null or has no visible content left; otherwise the cleaned
	 * text of at most {@code maxChars} code points (ellipsis included)
	 */
	public static @Nullable String plainText(@Nullable String raw, int maxChars) {
		if (raw == null || maxChars <= 0) {
			return null;
		}
		String text = LINE_BREAKS.matcher(raw).replaceAll(" ");
		text = stripHidden(text);
		text = BLANK_RUNS.matcher(text).replaceAll(" ").strip();
		return text.isEmpty() ? null : truncate(text, maxChars);
	}

	/** Removes control (except newline and tab), zero-width and bidi-override characters. */
	static String stripHidden(String text) {
		return HIDDEN_CHARS.matcher(text).replaceAll("");
	}

	/** Cuts to {@code maxChars} code points, ending with an ellipsis when something was removed. */
	static String truncate(String text, int maxChars) {
		int count = text.codePointCount(0, text.length());
		if (count <= maxChars) {
			return text;
		}
		int keep = Math.max(0, maxChars - ELLIPSIS.length());
		int end = text.offsetByCodePoints(0, Math.min(keep, count));
		return text.substring(0, end).stripTrailing() + ELLIPSIS;
	}

}
