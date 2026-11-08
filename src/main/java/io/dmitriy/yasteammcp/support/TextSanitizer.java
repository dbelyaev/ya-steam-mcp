package io.dmitriy.yasteammcp.support;

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

	private static final String ELLIPSIS = "\u2026";

	private TextSanitizer() {
	}

	/**
	 * Single-line inert text for a label or description. One streaming pass over the code points: line
	 * breaks and whitespace runs collapse to one space, hidden characters are dropped (see
	 * {@link #stripHidden}), and reading stops as soon as more than {@code maxChars} code points of
	 * output exist, so the cost is bounded by the cap and not by the input size.
	 *
	 * @return {@code null} when {@code raw} is null or contains no visible base character (only
	 * whitespace, marks, selectors or invisible characters); otherwise the cleaned text of at most
	 * {@code maxChars} code points (ellipsis included)
	 */
	public static @Nullable String plainText(@Nullable CharSequence raw, int maxChars) {
		if (raw == null || maxChars <= 0) {
			return null;
		}
		int length = raw.length();
		StringBuilder out = new StringBuilder(Math.min(length, maxChars + 2));
		int count = 0;
		boolean pendingSpace = false;
		boolean visible = false;
		boolean afterPictograph = false;
		for (int i = 0; i < length;) {
			int cp = Character.codePointAt(raw, i);
			i += Character.charCount(cp);
			boolean keep = keeps(cp, afterPictograph, i < length ? Character.codePointAt(raw, i) : -1);
			afterPictograph = pictographAfter(cp, afterPictograph);
			if (isSpaceLike(cp)) {
				pendingSpace = count > 0;
			}
			else if (keep) {
				if (pendingSpace) {
					out.append(' ');
					count++;
					pendingSpace = false;
				}
				out.appendCodePoint(cp);
				count++;
				visible |= isVisibleBase(cp);
				if (count > maxChars) {
					break;
				}
			}
		}
		return visible ? truncate(out.toString(), maxChars) : null;
	}

	/**
	 * Removes control characters (except newline and tab) and every Unicode format character
	 * (category Cf): zero-width and joiner characters, bidi controls and overrides, soft hyphen, word
	 * joiner, invisible operators, BOM, and the U+E0000 tag block that can smuggle invisible ASCII.
	 * The one exception is U+200D (zero width joiner), kept only when it sits between two extended
	 * pictographs (skipping variation selectors and skin tone modifiers before it), so emoji
	 * sequences such as a woman technologist survive. Works on code points without copying them into
	 * an array, so surrogate pairs are handled and extra memory is one output buffer.
	 */
	static String stripHidden(CharSequence text) {
		int length = text.length();
		StringBuilder out = new StringBuilder(length);
		boolean afterPictograph = false;
		for (int i = 0; i < length;) {
			int cp = Character.codePointAt(text, i);
			i += Character.charCount(cp);
			if (keeps(cp, afterPictograph, i < length ? Character.codePointAt(text, i) : -1)) {
				out.appendCodePoint(cp);
			}
			afterPictograph = pictographAfter(cp, afterPictograph);
		}
		return out.toString();
	}

	/** Newline and tab pass; control and format characters are dropped; a ZWJ survives inside emoji. */
	private static boolean keeps(int cp, boolean afterPictograph, int next) {
		if (cp == '\n' || cp == '\t') {
			return true;
		}
		int type = Character.getType(cp);
		if (type != Character.CONTROL && type != Character.FORMAT) {
			return true;
		}
		return cp == ZERO_WIDTH_JOINER && afterPictograph && next >= 0 && Character.isExtendedPictographic(next);
	}

	/** Whether the last code point that is not a selector or skin tone modifier is an extended pictograph. */
	private static boolean pictographAfter(int cp, boolean previous) {
		if (cp == VARIATION_SELECTOR_16 || Character.isEmojiModifier(cp)) {
			return previous;
		}
		return Character.isExtendedPictographic(cp);
	}

	private static boolean isSpaceLike(int cp) {
		return Character.isWhitespace(cp) || cp == '\u0085' || cp == '\u2028' || cp == '\u2029';
	}

	/** A base character a reader can see: not a mark, separator, control, format, unassigned, private or surrogate. */
	private static boolean isVisibleBase(int cp) {
		return switch (Character.getType(cp)) {
			case Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
					Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
					Character.CONTROL, Character.FORMAT, Character.UNASSIGNED, Character.PRIVATE_USE,
					Character.SURROGATE -> false;
			default -> true;
		};
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
