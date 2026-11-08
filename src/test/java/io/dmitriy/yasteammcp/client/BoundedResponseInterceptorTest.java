package io.dmitriy.yasteammcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import io.dmitriy.yasteammcp.client.BoundedResponseInterceptor.ResponseTooLargeException;

class BoundedResponseInterceptorTest {

	/** An endless source that records how many bytes were actually pulled from it. */
	private static final class CountingEndlessStream extends InputStream {

		long produced;

		@Override
		public int read() {
			produced++;
			return 'x';
		}

		@Override
		public int read(byte[] b, int off, int len) {
			produced += len;
			return len;
		}

	}

	@Test
	void read_whenSourceIsEndless_shouldAbortAfterAtMostLimitPlusOneByte() throws IOException {
		// Given
		var source = new CountingEndlessStream();
		try (InputStream bounded = BoundedResponseInterceptor.bound(source, 1000)) {
			// When / Then
			assertThatThrownBy(bounded::readAllBytes).isInstanceOf(ResponseTooLargeException.class)
				.hasMessage("Steam response exceeds the size limit");
		}
		assertThat(source.produced).isLessThanOrEqualTo(1001);
	}

	@Test
	void read_whenBodyEqualsLimit_shouldReturnItAll() throws IOException {
		// Given
		try (InputStream bounded = BoundedResponseInterceptor.bound(new ByteArrayInputStream(new byte[1000]), 1000)) {
			// When / Then
			assertThat(bounded.readAllBytes()).hasSize(1000);
		}
	}

	@Test
	void read_singleByteWhenOverLimit_shouldThrow() throws IOException {
		// Given
		try (InputStream bounded = BoundedResponseInterceptor.bound(new CountingEndlessStream(), 2)) {
			// When / Then
			assertThat(bounded.read()).isEqualTo('x');
			assertThat(bounded.read()).isEqualTo('x');
			assertThatThrownBy(bounded::read).isInstanceOf(ResponseTooLargeException.class);
		}
	}

}
