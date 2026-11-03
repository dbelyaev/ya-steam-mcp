package io.dmitriy.yasteammcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.dmitriy.yasteammcp.McpSpringBootTest;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;

/** Guards against Boot's own HTTP-service property configurers overriding our transport settings. */
@McpSpringBootTest(properties = { "steam.api.key=k", "steam.api.steam-id=76561197960287930", "steam.api.retries=0",
		"steam.api.read-timeout=1s", "steam.api.allow-insecure-test-base-url=true",
		"spring.http.serviceclient.steam.base-url=https://evil.example" })
class SteamClientBootWiringTest {

	@RegisterExtension
	static final WireMockExtension SERVER = WireMockExtension.newInstance().options(WireMockConfiguration.options().dynamicPort()).build();

	@Autowired
	SteamApiClient client;

	@DynamicPropertySource
	static void baseUrl(DynamicPropertyRegistry registry) {
		// resolved lazily while the context is created; the extension has started the server in beforeAll
		registry.add("steam.api.base-url", () -> "http://localhost:" + SERVER.getPort());
	}

	@Test
	void getOwnedGames_whenUpstreamIsSlow_shouldHonorConfiguredReadTimeout() {
		// Given
		SERVER.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/"))
			.willReturn(aResponse().withStatus(200).withBody("{\"response\":{}}").withFixedDelay(8000)));

		// When
		long start = System.nanoTime();
		var thrown = assertThatThrownBy(() -> client.getOwnedGames());
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		// Then
		thrown.isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
		// 1s configured timeout vs an 8s upstream delay: far apart so scheduler noise cannot flip the result
		assertThat(elapsedMs).isLessThan(5000);
	}

	@Test
	void getRecentlyPlayedGames_whenRedirected_shouldNotFollow() {
		// Given
		SERVER.stubFor(get(urlPathEqualTo("/IPlayerService/GetRecentlyPlayedGames/v1/"))
			.willReturn(aResponse().withStatus(302).withHeader("Location", "/elsewhere")));
		SERVER.stubFor(get(urlPathEqualTo("/elsewhere")).willReturn(aResponse().withStatus(200).withBody("{\"response\":{}}")));

		// When / Then
		assertThatThrownBy(() -> client.getRecentlyPlayedGames(5)).isInstanceOfSatisfying(SteamException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_BAD_RESPONSE));
		assertThat(SERVER.findAll(getRequestedFor(urlPathEqualTo("/elsewhere"))))
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
		SERVER.verify(1, getRequestedFor(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/")));
	}

}
