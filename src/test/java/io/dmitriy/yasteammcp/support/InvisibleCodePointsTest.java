package io.dmitriy.yasteammcp.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvisibleCodePointsTest {

	private static boolean inTable(int cp) {
		for (int[] range : InvisibleCodePoints.dicpRanges()) {
			if (cp >= range[0] && cp <= range[1]) {
				return true;
			}
		}
		return false;
	}

	@Test
	void dicpRanges_shouldBeSortedAndNotOverlapping() {
		int[][] ranges = InvisibleCodePoints.dicpRanges();

		for (int i = 0; i < ranges.length; i++) {
			assertThat(ranges[i][0]).isLessThanOrEqualTo(ranges[i][1]);
			if (i > 0) {
				assertThat(ranges[i][0]).isGreaterThan(ranges[i - 1][1]);
			}
		}
	}

	@Test
	void isInvisible_atRangeBoundaries_shouldBeTrueInsideAndFalseOutsideTheTable() {
		for (int[] range : InvisibleCodePoints.dicpRanges()) {
			assertThat(InvisibleCodePoints.isInvisible(range[0])).isTrue();
			assertThat(InvisibleCodePoints.isInvisible(range[1])).isTrue();
			if (!inTable(range[0] - 1)) {
				assertThat(InvisibleCodePoints.isInvisible(range[0] - 1)).isFalse();
			}
			if (!inTable(range[1] + 1)) {
				assertThat(InvisibleCodePoints.isInvisible(range[1] + 1)).isFalse();
			}
		}
	}

	@Test
	void isInvisible_forHangulFillersAndBlankBraille_shouldBeTrue() {
		for (int cp : new int[] { 0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800 }) {
			assertThat(InvisibleCodePoints.isInvisible(cp)).isTrue();
		}
	}

	@Test
	void isInvisible_forAscii_shouldBeFalse() {
		for (int cp = 0; cp < 0x80; cp++) {
			assertThat(InvisibleCodePoints.isInvisible(cp)).isFalse();
		}
	}

	@Test
	void isInvisible_forOrdinaryLettersAndEmoji_shouldBeFalse() {
		for (int cp : new int[] { 'A', 0x00E9, 0x4E2D, 0xD55C, 0x3042, 0x1F600, 0x2764, 0x2801, 0x3163, 0x3165 }) {
			assertThat(InvisibleCodePoints.isInvisible(cp)).isFalse();
		}
	}

	@Test
	void isVariationSelector_shouldCoverStandardSupplementaryAndMongolianSelectors() {
		for (int cp : new int[] { 0xFE00, 0xFE0F, 0xE0100, 0xE01EF, 0x180B, 0x180C, 0x180D, 0x180F }) {
			assertThat(InvisibleCodePoints.isVariationSelector(cp)).isTrue();
		}
		for (int cp : new int[] { 0xFDFF, 0xFE10, 0xE00FF, 0xE01F0, 0x180A, 0x180E, 'a' }) {
			assertThat(InvisibleCodePoints.isVariationSelector(cp)).isFalse();
		}
	}

}
