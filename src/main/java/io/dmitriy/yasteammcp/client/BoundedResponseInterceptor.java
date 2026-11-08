package io.dmitriy.yasteammcp.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Enforces the response size cap while the body is read, so a hostile or buggy upstream cannot make the
 * client buffer more than {@code maxBytes} (plus one read chunk). A declared {@code Content-Length} above the
 * cap is rejected before any byte is read; otherwise the body stream aborts as soon as the cap is exceeded
 * (chunked or lying upstream). Failure is an unchecked {@link ResponseTooLargeException} with a fixed message
 * (never an {@code IOException}, which would be treated as a retryable transport failure).
 */
public final class BoundedResponseInterceptor implements ClientHttpRequestInterceptor {

	public static final int DEFAULT_MAX_BYTES = 16 * 1024 * 1024;

	private final long maxBytes;

	public BoundedResponseInterceptor() {
		this(DEFAULT_MAX_BYTES);
	}

	public BoundedResponseInterceptor(long maxBytes) {
		this.maxBytes = maxBytes;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		ClientHttpResponse response = execution.execute(request, body);
		if (response.getHeaders().getContentLength() > maxBytes) {
			discard(response);
			throw new ResponseTooLargeException();
		}
		return new Bounded(response, maxBytes);
	}

	/**
	 * Closes the raw body stream first: the JDK-backed response drains the remaining body in {@code close()},
	 * which would read an oversize or endless body to the end and hold the connection (and concurrency slot).
	 */
	static void discard(ClientHttpResponse response) {
		try {
			response.getBody().close();
		}
		catch (IOException _) {
			// already failing; nothing to add
		}
		finally {
			response.close();
		}
	}

	/** Thrown when an upstream body exceeds the cap. Fixed message: nothing from the response or URI. */
	public static final class ResponseTooLargeException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public ResponseTooLargeException() {
			super("Steam response exceeds the size limit", null, false, false);
		}

	}

	/** A bounded view of a stream; package-private for tests of the counting behaviour. */
	static InputStream bound(InputStream in, long maxBytes) {
		return new LimitedStream(in, maxBytes);
	}

	private static final class Bounded implements ClientHttpResponse {

		private final ClientHttpResponse delegate;

		private final long maxBytes;

		Bounded(ClientHttpResponse delegate, long maxBytes) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
		}

		@Override
		public InputStream getBody() throws IOException {
			return new LimitedStream(delegate.getBody(), maxBytes);
		}

		@Override
		public HttpHeaders getHeaders() {
			return delegate.getHeaders();
		}

		@Override
		public HttpStatusCode getStatusCode() throws IOException {
			return delegate.getStatusCode();
		}

		@Override
		public String getStatusText() throws IOException {
			return delegate.getStatusText();
		}

		@Override
		public void close() {
			delegate.close();
		}

	}

	private static final class LimitedStream extends FilterInputStream {

		private final long max;

		private long count;

		LimitedStream(InputStream in, long max) {
			super(in);
			this.max = max;
		}

		@Override
		public int read() throws IOException {
			int b = in.read();
			if (b >= 0) {
				add(1);
			}
			return b;
		}

		@Override
		public int read(byte[] buf, int off, int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			// never ask for more than one byte past the cap: that byte proves the overflow
			int allowed = (int) Math.min(len, max - count + 1);
			int n = in.read(buf, off, allowed);
			if (n > 0) {
				add(n);
			}
			return n;
		}

		@Override
		public long skip(long n) throws IOException {
			long skipped = in.skip(Math.min(n, max - count + 1));
			if (skipped > 0) {
				add(skipped);
			}
			return skipped;
		}

		@Override
		public boolean markSupported() {
			return false;
		}

		private void add(long n) {
			count += n;
			if (count > max) {
				try {
					in.close();
				}
				catch (IOException _) {
					// the overflow is the error being reported
				}
				throw new ResponseTooLargeException();
			}
		}

	}

}
