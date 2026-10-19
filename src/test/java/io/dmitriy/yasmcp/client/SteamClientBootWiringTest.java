package io.dmitriy.yasmcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;

/** Guards against Boot's own HTTP-service property configurers overriding our transport settings. */
@SpringBootTest(properties = { "steam.api.key=k", "steam.api.steam-id=76561197960287930", "steam.api.retries=0",
		"steam.api.read-timeout=300ms", "steam.api.allow-insecure-test-base-url=true",
		"spring.ai.mcp.server.stdio=false", "spring.http.serviceclient.steam.base-url=https://evil.example" })
class SteamClientBootWiringTest {

	private static final WireMockServer SERVER = new WireMockServer(WireMockConfiguration.options().dynamicPort());

	@Autowired
	SteamApiClient client;

	@BeforeAll
	static void start() {
		SERVER.start();
	}

	@AfterAll
	static void stop() {
		SERVER.stop();
	}

	@DynamicPropertySource
	static void baseUrl(DynamicPropertyRegistry registry) {
		SERVER.start();
		registry.add("steam.api.base-url", () -> "http://localhost:" + SERVER.port());
	}

	@Test
	void getOwnedGames_whenUpstreamIsSlow_shouldHonorConfiguredReadTimeout() {
		// Given
		SERVER.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/"))
			.willReturn(aResponse().withStatus(200).withBody("{\"response\":{}}").withFixedDelay(2000)));

		// When
		long start = System.nanoTime();
		var thrown = assertThatThrownBy(() -> client.getOwnedGames());
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		// Then
		thrown.isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
		assertThat(elapsedMs).isLessThan(1500);
	}

	@Test
	void getOwnedGames_whenRedirected_shouldNotFollow() {
		// Given
		SERVER.stubFor(get(urlPathEqualTo("/IPlayerService/GetRecentlyPlayedGames/v1/"))
			.willReturn(aResponse().withStatus(302).withHeader("Location", "/elsewhere")));
		SERVER.stubFor(get(urlPathEqualTo("/elsewhere")).willReturn(aResponse().withStatus(200).withBody("{\"response\":{}}")));

		// When / Then
		assertThatThrownBy(() -> client.getRecentlyPlayedGames(5)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_BAD_RESPONSE));
		assertThat(SERVER.findAll(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/elsewhere"))))
			.isEmpty();
	}

	@Test
	void getOwnedGames_whenBootGroupBaseUrlIsSet_shouldStillUseAllowlistedSteamApiBaseUrl() {
		// Given: spring.http.serviceclient.steam.base-url points at a foreign host (see @SpringBootTest properties)
		SERVER.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/"))
			.willReturn(aResponse().withStatus(200).withBody("{\"response\":{}}")));

		// When
		var response = client.getOwnedGames();

		// Then: the request reached the validated steam.api.base-url, not the Boot group property
		assertThat(response.response()).isNotNull();
	}

}
