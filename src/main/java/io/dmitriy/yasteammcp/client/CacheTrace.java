package io.dmitriy.yasteammcp.client;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records, for one tool call, how many upstream reads were served from the cache. Bound with a
 * {@link ScopedValue} by the tool layer so services and clients keep their plain signatures;
 * outside a tool call it is a no-op.
 */
public final class CacheTrace {

	private static final ScopedValue<CacheTrace> CURRENT = ScopedValue.newInstance();

	private final AtomicInteger hits = new AtomicInteger();

	private final AtomicInteger misses = new AtomicInteger();

	/** Runs the action with a fresh trace bound and returns the trace afterwards via the callback. */
	public static <T> T within(CacheTrace trace, java.util.function.Supplier<T> action) {
		return ScopedValue.where(CURRENT, trace).call(action::get);
	}

	static void hit() {
		if (CURRENT.isBound()) {
			CURRENT.get().hits.incrementAndGet();
		}
	}

	static void miss() {
		if (CURRENT.isBound()) {
			CURRENT.get().misses.incrementAndGet();
		}
	}

	/** True only if at least one read happened and every one was a cache hit. */
	public boolean allFromCache() {
		return hits.get() > 0 && misses.get() == 0;
	}

}
