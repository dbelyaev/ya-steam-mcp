package io.dmitriy.yasteammcp.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TextSanitizerTest {

	@Test
	void plainText_whenNull_shouldReturnNull() {
		assertThat(TextSanitizer.plainText(null, 200)).isNull();
	}

	@Test
	void plainText_whenNonPositiveCap_shouldReturnNull() {
		assertThat(TextSanitizer.plainText("name", 0)).isNull();
	}

	@Test
	void plainText_whenBlankOrOnlyHidden_shouldReturnNull() {
		assertThat(TextSanitizer.plainText("   ", 200)).isNull();
		assertThat(TextSanitizer.plainText(Character.toString(0x202E) + Character.toString(0x200B) + "\u0000" + Character.toString(0xFEFF), 200)).isNull();
	}

	@Test
	void plainText_whenBidiAndZeroWidth_shouldStripThem() {
		assertThat(TextSanitizer.plainText("Ga" + Character.toString(0x202E) + "me" + Character.toString(0x2066) + "X" + Character.toString(0x2069) + Character.toString(0x200B) + Character.toString(0xFEFF), 200)).isEqualTo("GameX");
	}

	@Test
	void plainText_whenControlCharsAndNewlines_shouldBecomeSingleLine() {
		assertThat(TextSanitizer.plainText("Line1\r\nIgnore\tprevious\u0007\u2028rules", 200))
			.isEqualTo("Line1 Ignore previous rules");
	}

	@Test
	void plainText_whenOverLength_shouldTruncateWithEllipsis() {
		String result = TextSanitizer.plainText("x".repeat(300), TextSanitizer.MAX_NAME_CHARS);

		assertThat(result).hasSize(200).endsWith("\u2026");
	}

	@Test
	void plainText_whenExactlyAtCap_shouldKeepUnchanged() {
		assertThat(TextSanitizer.plainText("y".repeat(200), 200)).isEqualTo("y".repeat(200));
	}

	@Test
	void plainText_whenSurrogatePairsOverCap_shouldNotSplitCodePoint() {
		String result = TextSanitizer.plainText("\uD83D\uDE00".repeat(20), 5);

		assertThat(result).isEqualTo("\uD83D\uDE00".repeat(4) + "\u2026");
	}

	@Test
	void plainText_whenCjkEmojiAndPunctuation_shouldPreserve() {
		String name = "\u8D64\u3044\u9B54\u6CD5 \uD83C\uDFAE Caf\u00E9 <Deluxe> & Co.: Part II";

		assertThat(TextSanitizer.plainText(name, 200)).isEqualTo(name);
	}

	private static String cp(int... codePoints) {
		return new String(codePoints, 0, codePoints.length);
	}

	private static final int ZWJ = 0x200D;

	private static final int WOMAN = 0x1F469;

	private static final int LAPTOP = 0x1F4BB;

	@Test
	void plainText_whenZwjBetweenEmoji_shouldKeepSequence() {
		String technologist = cp(WOMAN, ZWJ, LAPTOP);
		String family = cp(0x1F468, ZWJ, 0x1F469, ZWJ, 0x1F467, ZWJ, 0x1F466);
		String withSelectors = cp(0x1F468, ZWJ, 0x2764, 0xFE0F, ZWJ, 0x1F468);
		String withSkinTone = cp(WOMAN, 0x1F3FD, ZWJ, LAPTOP);

		assertThat(TextSanitizer.plainText("Hi " + technologist, 200)).isEqualTo("Hi " + technologist);
		assertThat(TextSanitizer.plainText(family, 200)).isEqualTo(family);
		assertThat(TextSanitizer.plainText(withSelectors, 200)).isEqualTo(withSelectors);
		assertThat(TextSanitizer.plainText(withSkinTone, 200)).isEqualTo(withSkinTone);
	}

	@Test
	void plainText_whenZwjNotBetweenEmoji_shouldStripIt() {
		assertThat(TextSanitizer.plainText("a" + cp(ZWJ) + "b", 200)).isEqualTo("ab");
		assertThat(TextSanitizer.plainText(cp(ZWJ, WOMAN), 200)).isEqualTo(cp(WOMAN));
		assertThat(TextSanitizer.plainText(cp(WOMAN, ZWJ), 200)).isEqualTo(cp(WOMAN));
		assertThat(TextSanitizer.plainText(cp(WOMAN, ZWJ, ZWJ, LAPTOP), 200)).isEqualTo(cp(WOMAN, LAPTOP));
		assertThat(TextSanitizer.plainText(cp(WOMAN, ZWJ, 'x'), 200)).isEqualTo(cp(WOMAN) + "x");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0x061C, 0x2060, 0x2063, 0x00AD, 0x200C, 0x202E, 0x2066, 0x200B, 0xFEFF, 0x2069, 0x200F })
	void plainText_whenInvisibleFormatCharEmbedded_shouldRemoveIt(int invisible) {
		assertThat(TextSanitizer.plainText("ab" + cp(invisible) + "cd", 200)).isEqualTo("abcd");
	}

	@Test
	void plainText_whenOnlyInvisibleChars_shouldReturnNull() {
		assertThat(TextSanitizer.plainText(cp(0x2060, 0x00AD, 0x061C, 0x2063), 200)).isNull();
		assertThat(TextSanitizer.plainText(cp(ZWJ, 0x200C), 200)).isNull();
		assertThat(TextSanitizer.plainText(cp(0xE0041, 0xE0042), 200)).isNull();
	}

	@Test
	void plainText_whenTagCharacters_shouldRemoveThem() {
		String smuggled = cp(0xE0049, 0xE0067, 0xE006E, 0xE007F);

		assertThat(TextSanitizer.plainText("Game" + smuggled, 200)).isEqualTo("Game");
	}

	@Test
	void plainText_whenEmojiWithVariationSelectorAndCjk_shouldPreserve() {
		String heart = cp(0x2764, 0xFE0F) + " \u8D64\u3044\u9B54\u6CD5";

		assertThat(TextSanitizer.plainText(heart, 200)).isEqualTo(heart);
	}

	@Test
	void plainText_whenOnlyCombiningGraphemeJoinerOrSelectorsOrMarks_shouldReturnNull() {
		assertThat(TextSanitizer.plainText(cp(0x034F), 200)).isNull();
		assertThat(TextSanitizer.plainText(cp(0xFE0F), 200)).isNull();
		assertThat(TextSanitizer.plainText(cp(0xFE00, 0xFE0F, 0xE0100, 0xE01EF), 200)).isNull();
		assertThat(TextSanitizer.plainText(cp(0x0301, 0x0300, 0x20DD), 200)).isNull();
		assertThat(TextSanitizer.plainText(" " + cp(0x034F) + " ", 200)).isNull();
	}

	@Test
	void plainText_whenSelectorsAndMarksAttachedToBase_shouldPreserve() {
		String emoji = cp(0x2764, 0xFE0F);
		String accent = "e" + cp(0x0301);
		String cjkSelector = cp(0x8FBB, 0xE0100);

		assertThat(TextSanitizer.plainText(emoji, 200)).isEqualTo(emoji);
		assertThat(TextSanitizer.plainText(accent, 200)).isEqualTo(accent);
		assertThat(TextSanitizer.plainText(cjkSelector, 200)).isEqualTo(cjkSelector);
	}

	@Test
	void plainText_whenHugeInput_shouldStopReadingOnceCapIsKnown() {
		CountingSequence huge = new CountingSequence(20_000_000);

		String result = TextSanitizer.plainText(huge, 200);

		assertThat(result).hasSize(200).endsWith("\u2026");
		assertThat(huge.reads()).isLessThan(2_000);
	}

	@Test
	void plainText_whenLongInputWithSpacesAndInvisibles_shouldMatchBufferedResult() {
		String raw = "  a  b" + cp(0x2060) + "\t\nc " + "x".repeat(300);

		String result = TextSanitizer.plainText(raw, 10);

		assertThat(result).isEqualTo("a b c xxx\u2026");
	}

	@ParameterizedTest
	@ValueSource(ints = { ' ', 0x200B, 0x2060, 0x0301, 0x034F, 0x00A0 })
	void plainText_whenLongRunOfNothingVisibleBeforeTitle_shouldStopScanningAndReturnNull(int fill) {
		CountingSequence input = new CountingSequence(5_000_000, (char) fill, "Title");

		String result = TextSanitizer.plainText(input, 200);

		assertThat(result).isNull();
		assertThat(input.reads()).isLessThan(5_000);
	}

	@Test
	void plainText_whenVisibleTextThenHugeInvisibleRun_shouldReturnCollectedTextWithEllipsis() {
		String head = "Hello" + "\u200B".repeat(3);
		CountingSequence input = new CountingSequence(5_000_000, '\u2060', "Tail");

		assertThat(TextSanitizer.plainText(new Joined(head, input), 200)).isEqualTo("Hello\u2026");
		assertThat(input.reads()).isLessThan(5_000);
	}

	@Test
	void plainText_whenManyLeadingMarksThenTitle_shouldDropMarksAndKeepTitle() {
		assertThat(TextSanitizer.plainText(cp(0x034F).repeat(250) + "Game", 200)).isEqualTo("Game");
		assertThat(TextSanitizer.plainText(cp(0xFE0F).repeat(250) + "Game", 200)).isEqualTo("Game");
		assertThat(TextSanitizer.plainText(cp(0x0301).repeat(250) + " " + cp(0xE0100).repeat(5) + "Game", 200))
			.isEqualTo("Game");
		assertThat(TextSanitizer.plainText(cp(0x2060, 0x0301) + "Game", 200)).isEqualTo("Game");
	}

	@Test
	void plainText_whenMarksFollowVisibleBase_shouldStillKeepThem() {
		String text = "Game" + cp(0x0301) + " e" + cp(0x0301) + " " + cp(0x2764, 0xFE0F) + cp(0x8FBB, 0xE0100);

		assertThat(TextSanitizer.plainText(text, 200)).isEqualTo(text);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0x00A0, 0x2007, 0x202F, 0x3000, 0x2003 })
	void plainText_whenManySpaceSeparatorsBeforeTitle_shouldCollapseThem(int space) {
		assertThat(TextSanitizer.plainText(cp(space).repeat(201) + "Portal", 200)).isEqualTo("Portal");
		assertThat(TextSanitizer.plainText("a" + cp(space) + "b", 200)).isEqualTo("a b");
	}

	@Test
	void plainText_whenMixedUnicodeSpaceRuns_shouldCollapseToOneSpace() {
		String mixed = "a" + cp(0x00A0, ' ', 0x2007, 0x202F, 0x3000, '\t', 0x2003) + "b";

		assertThat(TextSanitizer.plainText(mixed, 200)).isEqualTo("a b");
	}

	/** Concatenation of a short string and a counting sequence, without copying either. */
	private record Joined(String head, CountingSequence rest) implements CharSequence {

		@Override
		public int length() {
			return head.length() + rest.length();
		}

		@Override
		public char charAt(int index) {
			return index < head.length() ? head.charAt(index) : rest.charAt(index - head.length());
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			throw new AssertionError("whole input must not be copied");
		}

		@Override
		public String toString() {
			throw new AssertionError("whole input must not be copied");
		}

	}


	/** Long run of one character, then a tail; records how much was read and refuses to be copied wholesale. */
	private static final class CountingSequence implements CharSequence {

		private final int length;

		private final char fill;

		private final String tail;

		private int reads;

		CountingSequence(int length) {
			this(length, 'a', "");
		}

		CountingSequence(int length, char fill, String tail) {
			this.length = length + tail.length();
			this.fill = fill;
			this.tail = tail;
		}

		int reads() {
			return reads;
		}

		@Override
		public int length() {
			return length;
		}

		@Override
		public char charAt(int index) {
			reads++;
			int tailStart = length - tail.length();
			return index < tailStart ? fill : tail.charAt(index - tailStart);
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			throw new AssertionError("whole input must not be copied");
		}

		@Override
		public String toString() {
			throw new AssertionError("whole input must not be copied");
		}

	}

}
