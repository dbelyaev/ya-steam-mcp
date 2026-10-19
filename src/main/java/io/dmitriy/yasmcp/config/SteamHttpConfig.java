package io.dmitriy.yasmcp.config;

import java.time.Clock;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

import io.dmitriy.yasmcp.client.Sleeper;
import io.dmitriy.yasmcp.client.SteamApiClient;
import io.dmitriy.yasmcp.client.SteamApiClientImpl;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class SteamHttpConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	Sleeper sleeper() {
		return Sleeper.SYSTEM;
	}

	@Bean
	public RestClient steamRestClient(RestClient.Builder builder, SteamProperties properties) {
		var api = properties.api();
		var settings = HttpClientSettings.defaults()
			.withTimeouts(api.connectTimeout(), api.readTimeout())
			.withRedirects(HttpRedirects.DONT_FOLLOW);
		return builder.baseUrl(api.baseUrl().toString())
			.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
			.build();
	}

	@Bean
	SteamApiClient steamApiClient(SteamProperties properties, RestClient steamRestClient, JsonMapper jsonMapper,
			Sleeper sleeper, Clock clock) {
		return new SteamApiClientImpl(properties, steamRestClient, jsonMapper, sleeper, clock);
	}

}
