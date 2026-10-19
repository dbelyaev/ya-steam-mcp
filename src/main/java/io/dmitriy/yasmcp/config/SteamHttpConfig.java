package io.dmitriy.yasmcp.config;

import java.time.Clock;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import io.dmitriy.yasmcp.client.Sleeper;
import io.dmitriy.yasmcp.client.SteamApiClient;
import io.dmitriy.yasmcp.client.SteamApiClientImpl;
import io.dmitriy.yasmcp.client.SteamWebApi;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
@ImportHttpServices(group = SteamHttpConfig.GROUP, types = SteamWebApi.class)
public class SteamHttpConfig {

	public static final String GROUP = "steam";

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	Sleeper sleeper() {
		return Sleeper.SYSTEM;
	}

	/**
	 * Transport for the "steam" HTTP service group: allowlisted base URL, explicit timeouts,
	 * redirects never followed, and no status exceptions (they would embed URI and body).
	 */
	@Bean
	RestClientHttpServiceGroupConfigurer steamGroupConfigurer(SteamProperties properties) {
		return groups -> groups.filterByName(GROUP).forEachClient((group, builder) -> customize(builder, properties.api()));
	}

	public static RestClient.Builder customize(RestClient.Builder builder, SteamProperties.Api api) {
		var settings = HttpClientSettings.defaults()
			.withTimeouts(api.connectTimeout(), api.readTimeout())
			.withRedirects(HttpRedirects.DONT_FOLLOW);
		return builder.baseUrl(api.baseUrl().toString())
			.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
			.defaultStatusHandler(status -> true, (request, response) -> {
			});
	}

	@Bean
	SteamApiClient steamApiClient(SteamProperties properties, SteamWebApi steamWebApi, JsonMapper jsonMapper,
			Sleeper sleeper, Clock clock) {
		return new SteamApiClientImpl(properties, steamWebApi, jsonMapper, sleeper, clock);
	}

}
