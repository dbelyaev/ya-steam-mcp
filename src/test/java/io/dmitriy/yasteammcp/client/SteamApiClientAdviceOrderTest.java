package io.dmitriy.yasteammcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.dmitriy.yasteammcp.TestProperties;
import io.dmitriy.yasteammcp.config.SteamHttpConfig;
import tools.jackson.databind.json.JsonMapper;

class SteamApiClientAdviceOrderTest {

	@RegisterExtension
	WireMockExtension server = WireMockExtension.newInstance().options(WireMockConfiguration.options().dynamicPort()).build();

	@Test
	void retrySleep_withConcurrencyLimitOfOne_shouldNotHoldTheSlot() throws Exception {
		// Given: limit 1, one retry after 1000ms, upstream always answers 500 immediately
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(500)));
		var api = TestProperties.api(URI.create("http://localhost:" + server.getPort()), "k", Duration.ofSeconds(2), 1,
				Duration.ofMillis(1000), Duration.ofSeconds(30), 1);
		var props = TestProperties.of(api);
		RestClient rc = SteamHttpConfig.customize(RestClient.builder(), api).build();
		SteamWebApi webApi = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rc)).build()
			.createClient(SteamWebApi.class);
		try (var clients = new ResilientClients()) {
			SteamApiClient client = clients.create(props, webApi, JsonMapper.builder().build());

			// When: two different calls start together
			try (var pool = Executors.newFixedThreadPool(2)) {
				var a = CompletableFuture.runAsync(() -> swallow(client::getOwnedGames), pool);
				var b = CompletableFuture.runAsync(() -> swallow(() -> client.getRecentlyPlayedGames(5)), pool);
				CompletableFuture.allOf(a, b).get();
			}

			// Then: arrival order proves the layering without any wall-clock assertion. Slot released during the
			// retry sleep => both first attempts arrive before either retry: [X, Y, X, Y]. Slot held across the
			// sleep => the first call's retry would arrive before the second call's first attempt: [X, X, Y, Y].
			var endpoints = server.getAllServeEvents().stream()
				.sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
				.map(e -> URI.create(e.getRequest().getUrl()).getPath())
				.toList();
			assertThat(endpoints).hasSize(4);
			assertThat(endpoints.get(0)).isNotEqualTo(endpoints.get(1));
		}
	}

	private static void swallow(Runnable call) {
		try {
			call.run();
		}
		catch (RuntimeException _) {
			// upstream always fails
		}
	}

}
