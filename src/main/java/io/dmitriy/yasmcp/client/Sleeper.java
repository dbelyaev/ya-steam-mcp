package io.dmitriy.yasmcp.client;

import java.time.Duration;

/** Seam so retry/backoff tests do not sleep for real. */
@FunctionalInterface
public interface Sleeper {

	void sleep(Duration duration) throws InterruptedException;

	Sleeper SYSTEM = duration -> Thread.sleep(duration);

}
