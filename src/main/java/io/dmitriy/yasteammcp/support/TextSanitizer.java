package io.dmitriy.yasteammcp.support;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Shared cleanup for untrusted Steam text that reaches an agent. Strips control and invisible format
 * characters (zero-width, bidi, tag characters) and caps the length; {@link NewsSanitizer} builds on the same primitives
 * for markup-heavy news. Markup is not interpreted here: a short label such as a game name keeps
 * its characters (CJK, emoji, punctuation) and is only made single-line and bounded.
 */
public final class TextSanitizer {

	/** Cap for names (games, achievements): longer than any real Steam title, short enough to be inert. */
	public static final int MAX_NAME_CHARS = 200;

	/** Cap for descriptions (achievements). */
	public static final int MAX_DESCRIPTION_CHARS = 500;

	private static final int ZERO_WIDTH_JOINER = 0x200D;

	private static final int VARIATION_SELECTOR_16 = 0xFE0F;

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

	/**
	 * Removes control characters (except newline and tab) and every Unicode format character
	 * (category Cf): zero-width and joiner characters, bidi controls and overrides, soft hyphen, word
	 * joiner, invisible operators, BOM, and the U+E0000 tag block that can smuggle invisible ASCII.
	 * The one exception is U+200D (zero width joiner), kept only when it sits between two extended
	 * pictographs (skipping variation selectors and skin tone modifiers before it), so emoji
	 * sequences such as a woman technologist survive. Works on code points, so surrogate pairs are
	 * handled.
	 */
	static String stripHidden(String text) {
		int[] cps = text.codePoints().toArray();
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < cps.length; i++) {
			int cp = cps[i];
			if (cp == '\n' || cp == '\t' || !isHidden(cp) || isJoinerInsideEmoji(cps, i)) {
				out.appendCodePoint(cp);
			}
		}
		return out.toString();
	}

	private static boolean isHidden(int cp) {
		int type = Character.getType(cp);
		return type == Character.CONTROL || type == Character.FORMAT;
	}

	private static boolean isJoinerInsideEmoji(int[] cps, int index) {
		if (cps[index] != ZERO_WIDTH_JOINER || index + 1 >= cps.length
				|| !Character.isExtendedPictographic(cps[index + 1])) {
			return false;
		}
		int before = index - 1;
		while (before >= 0 && (cps[before] == VARIATION_SELECTOR_16 || Character.isEmojiModifier(cps[before]))) {
			before--;
		}
		return before >= 0 && Character.isExtendedPictographic(cps[before]);
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
