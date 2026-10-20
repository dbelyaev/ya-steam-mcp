package io.dmitriy.yasmcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.dmitriy.yasmcp.config.SteamHttpConfig;
import io.dmitriy.yasmcp.config.SteamProperties;
import tools.jackson.databind.json.JsonMapper;

class SteamApiClientAdviceOrderTest {

	@Test
	void retrySleep_withConcurrencyLimitOfOne_shouldNotHoldTheSlot() throws Exception {
		// Given: limit 1, one retry after 1000ms, upstream always answers 500 immediately
		var server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		server.start();
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(500)));
		var api = new SteamProperties.Api(URI.create("http://localhost:" + server.port()), "k", "76561197960287930",
				Duration.ofSeconds(1), Duration.ofSeconds(2), 1, Duration.ofMillis(1000), Duration.ofSeconds(30), 1,
				"english", true);
		var props = new SteamProperties(api, new SteamProperties.Library(true), new SteamProperties.Store(false), SteamProperties.Cache.defaults());
		RestClient rc = SteamHttpConfig.customize(RestClient.builder(), api).build();
		SteamWebApi webApi = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rc)).build()
			.createClient(SteamWebApi.class);
		try (var clients = new ResilientClients()) {
			SteamApiClient client = clients.create(props, webApi, JsonMapper.builder().build());
			var pool = Executors.newFixedThreadPool(2);

			// When: two calls start together
			long start = System.nanoTime();
			var a = CompletableFuture.runAsync(() -> swallow(client), pool);
			var b = CompletableFuture.runAsync(() -> swallow(client), pool);
			CompletableFuture.allOf(a, b).get();
			long elapsedMs = (System.nanoTime() - start) / 1_000_000;
			pool.shutdown();

			// Then: sleeps overlap (about 1.05 s). If the slot were held across the sleep, the calls would
			// serialize to about 2.1 s. The 1 s delay keeps the two outcomes far apart under CPU load.
			assertThat(elapsedMs).isLessThan(1700);
		}
		finally {
			server.stop();
		}
	}

	private static void swallow(SteamApiClient client) {
		try {
			client.getOwnedGames();
		}
		catch (RuntimeException _) {
			// upstream always fails
		}
	}

}
