package io.dmitriy.yasmcp.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.resilience.annotation.EnableResilientMethods;

import io.dmitriy.yasmcp.config.SteamProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds {@link SteamApiClientImpl} and {@link ConcurrencyLimitedSteamWebApi} behind the same
 * Spring proxies production uses ({@code @Retryable}, {@code @ConcurrencyLimit}), with the placeholders those annotations
 * read taken from the given properties. Contexts are closed with {@link #close()}.
 */
final class ResilientClients implements AutoCloseable {

	@Configuration(proxyBeanMethods = false)
	@EnableResilientMethods
	static class ResilienceOnly {

	}

	private final List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();

	SteamApiClient create(SteamProperties props, SteamWebApi webApi, JsonMapper mapper) {
		var api = props.api();
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("steam.api.retries", api.retries());
		source.put("steam.api.retry-delay", api.retryDelay().toMillis() + "ms");
		source.put("steam.api.total-timeout", api.totalTimeout().toMillis() + "ms");
		source.put("steam.api.max-concurrent-requests", api.maxConcurrentRequests());
		var ctx = new AnnotationConfigApplicationContext();
		ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("steam-test", source));
		ctx.register(ResilienceOnly.class);
		// same layering as production: retry (client) -> concurrency limit (web api) -> http
		ctx.registerBean("limitedWebApi", SteamWebApi.class, () -> new ConcurrencyLimitedSteamWebApi(webApi));
		ctx.registerBean("steamApiClient", SteamApiClient.class,
				() -> new SteamApiClientImpl(props, ctx.getBean(SteamWebApi.class), mapper));
		ctx.refresh();
		contexts.add(ctx);
		return ctx.getBean(SteamApiClient.class);
	}

	@Override
	public void close() {
		contexts.forEach(AnnotationConfigApplicationContext::close);
		contexts.clear();
	}

}
