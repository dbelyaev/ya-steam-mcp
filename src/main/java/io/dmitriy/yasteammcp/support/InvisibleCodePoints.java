package io.dmitriy.yasteammcp.support;

/**
 * What "renders as nothing" means for {@link TextSanitizer}: the Unicode
 * {@code Default_Ignorable_Code_Point} (DICP) property plus a short best-effort list of
 * characters that are not DICP but draw a blank glyph.
 *
 * <p>The DICP table is copied verbatim from the Unicode Character Database, version 16.0.0:
 * {@code https://www.unicode.org/Public/16.0.0/ucd/DerivedCoreProperties.txt}
 * (section {@code Default_Ignorable_Code_Point}, 27 ranges, listed as the file lists them, so some
 * ranges are adjacent). 16.0 is the Unicode version of the JDK this project builds on (Java 25).
 * <b>Re-check the table when the JDK or the Unicode version is upgraded.</b>
 *
 * <p>DICP covers, among others, soft hyphen, combining grapheme joiner, Arabic letter mark, the
 * Hangul fillers (U+115F, U+1160, U+3164, U+FFA0), Khmer inherent vowels, Mongolian variation
 * selectors and vowel separator, zero-width and bidi format characters, invisible operators,
 * variation selectors, the interlinear/shorthand/musical format ranges and the whole tag block.
 *
 * <p>The blank-glyph list is best effort and not a standard: it only holds characters that look
 * empty in common fonts without being default ignorable. Per the project's stop rule, a future
 * finding is a table entry plus a test, not a new mechanism.
 */
final class InvisibleCodePoints {

	/** {@code Default_Ignorable_Code_Point}, Unicode 16.0.0, sorted, inclusive ranges. */
	private static final int[][] DICP = {
		{ 0x00AD, 0x00AD },
		{ 0x034F, 0x034F },
		{ 0x061C, 0x061C },
		{ 0x115F, 0x1160 },
		{ 0x17B4, 0x17B5 },
		{ 0x180B, 0x180D },
		{ 0x180E, 0x180E },
		{ 0x180F, 0x180F },
		{ 0x200B, 0x200F },
		{ 0x202A, 0x202E },
		{ 0x2060, 0x2064 },
		{ 0x2065, 0x2065 },
		{ 0x2066, 0x206F },
		{ 0x3164, 0x3164 },
		{ 0xFE00, 0xFE0F },
		{ 0xFEFF, 0xFEFF },
		{ 0xFFA0, 0xFFA0 },
		{ 0xFFF0, 0xFFF8 },
		{ 0x1BCA0, 0x1BCA3 },
		{ 0x1D173, 0x1D17A },
		{ 0xE0000, 0xE0000 },
		{ 0xE0001, 0xE0001 },
		{ 0xE0002, 0xE001F },
		{ 0xE0020, 0xE007F },
		{ 0xE0080, 0xE00FF },
		{ 0xE0100, 0xE01EF },
		{ 0xE01F0, 0xE0FFF },
	};

	/** Not DICP, but blank in common fonts: U+2800 BRAILLE PATTERN BLANK. Sorted. */
	private static final int[] BLANK_GLYPHS = { 0x2800 };

	private InvisibleCodePoints() {
	}

	/** @return true for a default ignorable code point or a listed blank-glyph character */
	static boolean isInvisible(int cp) {
		if (cp < 0x80) {
			return false;
		}
		int low = 0;
		int high = DICP.length - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (cp < DICP[mid][0]) {
				high = mid - 1;
			}
			else if (cp > DICP[mid][1]) {
				low = mid + 1;
			}
			else {
				return true;
			}
		}
		return java.util.Arrays.binarySearch(BLANK_GLYPHS, cp) >= 0;
	}

	/** Standard (FE00-FE0F), supplementary (E0100-E01EF) and Mongolian free (180B-180D, 180F) variation selectors. */
	static boolean isVariationSelector(int cp) {
		return (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF) || (cp >= 0x180B && cp <= 0x180D)
				|| cp == 0x180F;
	}

	/** Test access to the table. */
	static int[][] dicpRanges() {
		return DICP;
	}

}
