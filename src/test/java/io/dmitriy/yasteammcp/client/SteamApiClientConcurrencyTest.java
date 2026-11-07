package io.dmitriy.yasteammcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.dmitriy.yasteammcp.TestProperties;
import io.dmitriy.yasteammcp.config.SteamHttpConfig;
import io.dmitriy.yasteammcp.TestProperties;
import io.dmitriy.yasteammcp.config.SteamProperties;
import tools.jackson.databind.json.JsonMapper;

class SteamApiClientConcurrencyTest {

	private static final String BODY = "{\"response\":{\"games\":[]},\"playerstats\":{}}";

	@Import(SteamHttpConfig.class)
	static class Cfg {

		@Bean
		SteamProperties steamProperties(@Value("${test.base-url}") URI baseUrl) {
			return TestProperties.of(TestProperties.api(baseUrl, "k", Duration.ofSeconds(5), 0, Duration.ofMillis(200),
					Duration.ofSeconds(30), 2));
		}

		@Bean
		JsonMapper jsonMapper() {
			return JsonMapper.builder().build();
		}

		@Bean
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}

	}

	@RegisterExtension
	WireMockExtension server = WireMockExtension.newInstance().options(WireMockConfiguration.options().dynamicPort()).build();

	@Test
	void calls_withLimitOfTwo_shouldBeSharedAcrossAllClientMethods() throws Exception {
		// Given: each upstream call takes 300ms, limit property is 2
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "application/json").withBody(BODY).withFixedDelay(300)));
		try (var ctx = new AnnotationConfigApplicationContext()) {
			ctx.getEnvironment().getPropertySources()
				.addFirst(new MapPropertySource("t", java.util.Map.of("steam.api.max-concurrent-requests", "2",
					"test.base-url", "http://localhost:" + server.getPort())));
			ctx.register(Cfg.class);
			ctx.refresh();
			SteamApiClient client = ctx.getBean(SteamApiClient.class);

			// When: 3 owned-games + 3 recent-games calls fired at once (6 x 300ms)
			long elapsedMs;
			try (var pool = Executors.newFixedThreadPool(6)) {
				long start = System.nanoTime();
				List<CompletableFuture<?>> calls = new ArrayList<>();
				for (int i = 0; i < 3; i++) {
					calls.add(CompletableFuture.runAsync(client::getOwnedGames, pool));
					calls.add(CompletableFuture.runAsync(() -> client.getRecentlyPlayedGames(5), pool));
				}
				CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).get();
				elapsedMs = (System.nanoTime() - start) / 1_000_000;
			}

			// Then: two at a time => at least 3 sequential waves of ~300ms
			assertThat(elapsedMs).isGreaterThanOrEqualTo(850);
		}
	}

}
