package io.dmitriy.yasteammcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
		// Given: limit 1, one retry, upstream always answers 500 immediately. Call A's retry attempt is paused by a
		// gate between the retry advice and the concurrency limit until the test has seen call B reach Steam.
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(500)));
		var api = TestProperties.api(URI.create("http://localhost:" + server.getPort()), "k", Duration.ofSeconds(2), 1,
				Duration.ofMillis(50), Duration.ofSeconds(60), 1);
		var props = TestProperties.of(api);
		RestClient rc = SteamHttpConfig.customize(RestClient.builder(), api).build();
		SteamWebApi webApi = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rc)).build()
			.createClient(SteamWebApi.class);
		var release = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try (var clients = new ResilientClients()) {
			SteamApiClient client = clients.create(props, webApi, JsonMapper.builder().build(),
					limited -> gateSecondOwnedGamesCall(limited, release));

			// When: both calls start
			pool.execute(() -> swallow(client::getOwnedGames));
			pool.execute(() -> swallow(() -> client.getRecentlyPlayedGames(5)));

			// Then: B reaches Steam although A is between its first attempt and its retry. If A kept the concurrency
			// slot through its backoff, B could never enter and no recently-played request would arrive; the timeout
			// is only a ceiling for that failing case, the passing case does not wait on it.
			await().alias("recently-played request arrives while the owned-games call waits for its retry")
				.atMost(Duration.ofSeconds(10))
				.until(() -> server.getAllServeEvents().stream()
					.anyMatch(e -> e.getRequest().getUrl().startsWith("/IPlayerService/GetRecentlyPlayedGames/v1/")));
			release.countDown();
			// A's retry then completes: one owned-games request before the gate, one after
			await().atMost(Duration.ofSeconds(10)).until(() -> server.getAllServeEvents().stream()
				.filter(e -> e.getRequest().getUrl().startsWith("/IPlayerService/GetOwnedGames/v1/")).count() == 2);
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	/** Pauses the second owned-games call, i.e. the retry, until {@code release} is counted down. */
	private static SteamWebApi gateSecondOwnedGamesCall(SteamWebApi target, CountDownLatch release) {
		var ownedGamesCalls = new AtomicInteger();
		return (SteamWebApi) Proxy.newProxyInstance(SteamWebApi.class.getClassLoader(),
				new Class<?>[] { SteamWebApi.class }, (proxy, method, args) -> {
					if (method.getName().equals("getOwnedGames") && ownedGamesCalls.incrementAndGet() == 2
							&& !release.await(30, TimeUnit.SECONDS)) {
						throw new IllegalStateException("gate was never released");
					}
					try {
						return method.invoke(target, args);
					}
					catch (InvocationTargetException ex) {
						throw ex.getCause();
					}
				});
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
