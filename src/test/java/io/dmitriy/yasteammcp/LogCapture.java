package io.dmitriy.yasteammcp;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Captures Logback events for a logger until {@link #close()}; the captured events stay readable afterwards. */
public final class LogCapture implements AutoCloseable {

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	private final Logger logger;

	private LogCapture(Logger logger) {
		this.logger = logger;
		appender.start();
		logger.addAppender(appender);
	}

	public static LogCapture ofRoot() {
		return new LogCapture((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
	}

	public static LogCapture of(Class<?> type) {
		return new LogCapture((Logger) LoggerFactory.getLogger(type));
	}

	public List<ILoggingEvent> events() {
		return appender.list;
	}

	@Override
	public void close() {
		logger.detachAppender(appender);
		appender.stop();
	}

}
