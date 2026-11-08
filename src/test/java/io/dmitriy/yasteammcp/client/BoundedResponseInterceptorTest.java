package io.dmitriy.yasteammcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

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

	/** Endless body; close() marks closed, and a drain after close fails like a real closed stream. */
	private static final class TrackingStream extends InputStream {

		final java.util.List<String> events;

		long produced;

		boolean closed;

		TrackingStream(java.util.List<String> events) {
			this.events = events;
		}

		@Override
		public int read() throws IOException {
			return read(new byte[1], 0, 1);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (closed) {
				throw new IOException("closed");
			}
			produced += len;
			return len;
		}

		@Override
		public void close() {
			closed = true;
			events.add("body.close");
		}

	}

	/** Like the JDK-backed response: close() drains the remaining body (until EOF) and then closes. */
	private static final class DrainingResponse implements ClientHttpResponse {

		private final TrackingStream body;

		private final HttpHeaders headers = new HttpHeaders();

		DrainingResponse(TrackingStream body, long contentLength) {
			this.body = body;
			if (contentLength >= 0) {
				headers.setContentLength(contentLength);
			}
		}

		@Override
		public InputStream getBody() {
			return body;
		}

		@Override
		public HttpHeaders getHeaders() {
			return headers;
		}

		@Override
		public HttpStatusCode getStatusCode() {
			return HttpStatus.OK;
		}

		@Override
		public String getStatusText() {
			return "OK";
		}

		@Override
		public void close() {
			body.events.add("response.close");
			try {
				// a real drain would loop until EOF: bounded here so a regression fails instead of hanging
				while (body.produced < 10_000_000 && body.read() >= 0) {
					// keep reading
				}
			}
			catch (IOException _) {
				// closed stream: drain stops
			}
		}

	}

	@Test
	void intercept_whenContentLengthOverLimit_shouldCloseBodyBeforeResponseAndNotDrain() throws IOException {
		// Given
		var events = new java.util.ArrayList<String>();
		var body = new TrackingStream(events);
		var interceptor = new BoundedResponseInterceptor(1000);
		var request = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://localhost/x"));

		// When / Then
		assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], (r, b) -> new DrainingResponse(body, 5000)))
			.isInstanceOf(ResponseTooLargeException.class);
		assertThat(events).containsExactly("body.close", "response.close");
		assertThat(body.produced).isZero();
	}

	@Test
	void read_whenOverflow_shouldCloseSourceBeforeThrowingAndStopReading() throws IOException {
		// Given
		var events = new java.util.ArrayList<String>();
		var body = new TrackingStream(events);

		// When / Then
		try (InputStream bounded = BoundedResponseInterceptor.bound(body, 1000)) {
			assertThatThrownBy(bounded::readAllBytes).isInstanceOf(ResponseTooLargeException.class);
		}
		assertThat(events).startsWith("body.close");
		assertThat(body.closed).isTrue();
		assertThat(body.produced).isLessThanOrEqualTo(1001);
		// a later response cleanup finds a closed stream and cannot drain
		new DrainingResponse(body, -1).close();
		assertThat(body.produced).isLessThanOrEqualTo(1001);
	}

}
