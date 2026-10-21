package io.dmitriy.yasteammcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;

class LibraryQueryTest {

	@Test
	void of_withAllNull_shouldApplyDefaults() {
		var query = LibraryQuery.of(null, null, null, null, null);

		assertThat(query.nameFilter()).isNull();
		assertThat(query.sortBy()).isEqualTo(SortBy.DEFAULT);
		assertThat(query.offset()).isZero();
		assertThat(query.limit()).isEqualTo(LibraryQuery.DEFAULT_LIMIT);
		assertThat(query.includeNeverPlayed()).isTrue();
	}

	@Test
	void of_withPaddedMixedCaseQuery_shouldTrimAndLowercase() {
		assertThat(LibraryQuery.of("  HaLf-Life ", null, null, null, false).nameFilter()).isEqualTo("half-life");
		assertThat(LibraryQuery.of("   ", null, null, null, false).nameFilter()).isNull();
	}

	@Test
	void of_withBoundaryValues_shouldAccept() {
		var query = LibraryQuery.of("a".repeat(LibraryQuery.MAX_QUERY_LENGTH), "name_asc", LibraryQuery.MAX_OFFSET,
				LibraryQuery.MAX_LIMIT, false);

		assertThat(query.sortBy()).isEqualTo(SortBy.NAME_ASC);
		assertThat(query.offset()).isEqualTo(LibraryQuery.MAX_OFFSET);
		assertThat(query.limit()).isEqualTo(LibraryQuery.MAX_LIMIT);
		assertThat(query.includeNeverPlayed()).isFalse();
	}

	@Test
	void of_withTooLongQuery_shouldRejectAsInvalidArgument() {
		assertInvalid(() -> LibraryQuery.of("a".repeat(LibraryQuery.MAX_QUERY_LENGTH + 1), null, null, null, null),
				"query must be at most 100 characters");
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, LibraryQuery.MAX_OFFSET + 1 })
	void of_withOffsetOutOfRange_shouldRejectAsInvalidArgument(int offset) {
		assertInvalid(() -> LibraryQuery.of(null, null, offset, null, null), "offset must be between 0 and 100000");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -5, LibraryQuery.MAX_LIMIT + 1 })
	void of_withLimitOutOfRange_shouldRejectAsInvalidArgument(int limit) {
		assertInvalid(() -> LibraryQuery.of(null, null, null, limit, null), "limit must be between 1 and 100");
	}

	private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String message) {
		assertThatThrownBy(call).isInstanceOfSatisfying(SteamException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT))
			.hasMessage(message);
	}

}
