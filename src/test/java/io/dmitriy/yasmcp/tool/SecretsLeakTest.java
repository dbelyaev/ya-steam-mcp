package io.dmitriy.yasmcp.tool;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.dmitriy.yasmcp.McpSpringBootTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Every tool, under every upstream failure mode, with a recognizable key and upstream body:
 * neither may appear in a tool result or in any log line, and no result may carry a URL.
 */
@McpSpringBootTest(properties = { "steam.api.key=LEAKKEY-9f3a77c1", "steam.api.steam-id=76561197960287930",
		"steam.api.allow-insecure-test-base-url=true", "steam.api.retries=1", "steam.api.retry-delay=1ms",
		"steam.api.read-timeout=400ms", "steam.cache.enabled=false" })
class SecretsLeakTest {

	private static final String KEY = "LEAKKEY-9f3a77c1";

	private static final String BODY_SECRET = "UPSTREAM-BODY-SECRET-5521";

	private static final WireMockServer SERVER = new WireMockServer(WireMockConfiguration.options().dynamicPort());

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	@Autowired
	SteamTools tools;

	@DynamicPropertySource
	static void baseUrl(DynamicPropertyRegistry registry) {
		SERVER.start();
		registry.add("steam.api.base-url", () -> "http://localhost:" + SERVER.port());
	}

	@BeforeAll
	static void start() {
		SERVER.start();
	}

	@AfterAll
	static void stop() {
		SERVER.stop();
	}

	@BeforeEach
	void attach() {
		SERVER.resetAll();
		logs.start();
		((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
	}

	@AfterEach
	void detach() {
		((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
	}

	static Stream<Arguments> failures() {
		return Stream.of(
				Arguments.of("401 with key in body", aResponse().withStatus(401).withBody(KEY + BODY_SECRET)),
				Arguments.of("403", aResponse().withStatus(403).withBody(BODY_SECRET)),
				Arguments.of("429", aResponse().withStatus(429).withHeader("Retry-After", "1").withBody(BODY_SECRET)),
				Arguments.of("500", aResponse().withStatus(500).withBody(BODY_SECRET + KEY)),
				Arguments.of("connection reset", aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
				Arguments.of("empty response", aResponse().withFault(Fault.EMPTY_RESPONSE)),
				Arguments.of("malformed 200", aResponse().withStatus(200).withBody("{\"response\":" + BODY_SECRET + KEY)),
				Arguments.of("redirect", aResponse().withStatus(302).withHeader("Location", "http://evil.example/" + KEY)),
				Arguments.of("read timeout", aResponse().withStatus(200).withBody("{}").withFixedDelay(1200)),
				Arguments.of("400 html", aResponse().withStatus(400).withBody("<html>" + BODY_SECRET + KEY + "</html>")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("failures")
	void allTools_whenUpstreamMisbehaves_shouldNeverExposeKeyBodyOrUrlInResultsOrLogs(String name,
			ResponseDefinitionBuilder response) {
		// Given
		SERVER.stubFor(get(anyUrl()).willReturn(response));

		// When
		List<CallToolResult> results = new ArrayList<>(List.of(tools.getLibrary(null, null, null, null, null),
				tools.getPlaytime(620L), tools.getRecentGames(null), tools.getAchievements(620L, null, null, null),
				tools.getGameNews(620L, null, null), tools.getGameDetails(620L, null)));

		// Then
		for (CallToolResult result : results) {
			String text = ((TextContent) result.content().getFirst()).text();
			assertThat(text).as("result under '%s'", name)
				.doesNotContain(KEY)
				.doesNotContain(BODY_SECRET)
				.doesNotContain("http://")
				.doesNotContain("https://")
				.doesNotContain("key=")
				.doesNotContain("localhost");
		}
		// the log checks below must not be vacuous: the failures above are logged (tool outcome at least)
		assertThat(logs.list).as("log events under '%s'", name).isNotEmpty();
		for (ILoggingEvent event : new ArrayList<>(logs.list)) {
			assertThat(event.getFormattedMessage()).as("log under '%s'", name)
				.doesNotContain(KEY)
				.doesNotContain(BODY_SECRET)
				.doesNotContain("key=");
			assertThat(event.getThrowableProxy()).as("stack trace under '%s'", name).isNull();
		}
	}

}
