package io.dmitriy.yasteammcp.client;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;

/** Manually advanced clock for cache TTL tests. */
final class FakeTicker implements Ticker {

	private final AtomicLong nanos = new AtomicLong();

	@Override
	public long read() {
		return nanos.get();
	}

	void advance(Duration duration) {
		nanos.addAndGet(duration.toNanos());
	}

}
