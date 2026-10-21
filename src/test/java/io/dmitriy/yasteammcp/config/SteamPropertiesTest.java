package io.dmitriy.yasteammcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;

class SteamPropertiesTest {

	@Configuration
	@EnableConfigurationProperties(SteamProperties.class)
	static class Cfg {

	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Cfg.class);

	@Test
	void binding_withoutCredentials_shouldStartAndFailAtCallTime() {
		// Given: no key, no SteamID
		runner.run(ctx -> {
			// When
			assertThat(ctx).hasNotFailed();
			var api = ctx.getBean(SteamProperties.class).api();

			// Then
			assertThat(api.baseUrl()).hasToString("https://api.steampowered.com");
			assertThat(api.retries()).isEqualTo(2);
			assertThatThrownBy(api::requireKey).isInstanceOfSatisfying(SteamException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFIGURATION_MISSING));
			assertThatThrownBy(api::requireSteamId).isInstanceOfSatisfying(SteamException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFIGURATION_MISSING));
		});
	}

	@Test
	void binding_withMalformedSteamId_shouldStartAndFailAtCallTimeWithoutEcho() {
		// Given
		runner.withPropertyValues("steam.api.steam-id=not-an-id-SECRET").run(ctx -> {
			// When
			assertThat(ctx).hasNotFailed();
			var api = ctx.getBean(SteamProperties.class).api();

			// Then
			assertThatThrownBy(api::requireSteamId).isInstanceOf(SteamException.class)
				.hasMessageNotContaining("SECRET");
		});
	}

	@Test
	void binding_withCredentials_shouldReturnUnchanged() {
		// Given
		runner.withPropertyValues("steam.api.steam-id=76561197960287930", "steam.api.key=abc").run(ctx -> {
			// When
			var api = ctx.getBean(SteamProperties.class).api();

			// Then
			assertThat(api.requireSteamId()).isEqualTo("76561197960287930");
			assertThat(api.requireKey()).isEqualTo("abc");
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://api.steampowered.com", "https://partner.steam-api.com",
			"https://api.steampowered.com.evil.example", "https://evil.example@api.steampowered.com",
			"https://api.steampowered.com/IPlayerService", "https://api.steampowered.com:8443",
			"http://localhost:8080", "http://127.0.0.1:8080" })
	void binding_withNonOfficialOrigin_shouldFailStartup(String url) {
		// Given / When / Then
		runner.withPropertyValues("steam.api.base-url=" + url).run(ctx -> assertThat(ctx).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:8080", "http://127.0.0.1:9999", "https://localhost" })
	void binding_withTestOverrideAndLoopback_shouldStart(String url) {
		// Given / When / Then
		runner.withPropertyValues("steam.api.base-url=" + url, "steam.api.allow-insecure-test-base-url=true")
			.run(ctx -> assertThat(ctx).hasNotFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://evil.example", "https://partner.steam-api.com", "http://localhost.evil.example" })
	void binding_withTestOverrideAndNonLoopback_shouldFailStartup(String url) {
		// Given / When / Then
		runner.withPropertyValues("steam.api.base-url=" + url, "steam.api.allow-insecure-test-base-url=true")
			.run(ctx -> assertThat(ctx).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "steam.api.retries=-1", "steam.api.retries=6", "steam.api.connect-timeout=0s",
			"steam.api.read-timeout=10m", "steam.api.total-timeout=0s", "steam.api.max-concurrent-requests=0",
			"steam.api.max-concurrent-requests=17" })
	void binding_withUnsafeLimit_shouldFailStartup(String property) {
		// Given / When / Then
		runner.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	void toString_withCredentials_shouldNotContainKeyOrSteamId() {
		// Given
		runner.withPropertyValues("steam.api.steam-id=76561197960287930", "steam.api.key=SECRETKEY123").run(ctx -> {
			var props = ctx.getBean(SteamProperties.class);

			// When
			String text = props.toString() + props.api();

			// Then
			assertThat(text).doesNotContain("SECRETKEY123").doesNotContain("76561197960287930").contains("<redacted>");
		});
	}

	@Test
	void binding_withEnvironmentVariablePlaceholders_shouldPopulateKeyAndSteamId() {
		// Given: application.yml maps STEAM_API_KEY / STEAM_ID onto steam.api.*
		runner.withInitializer(new ConfigDataApplicationContextInitializer())
			.withPropertyValues("STEAM_API_KEY=envkey", "STEAM_ID=76561197960287930")
			.run(ctx -> {
				// When
				var api = ctx.getBean(SteamProperties.class).api();

				// Then
				assertThat(api.requireKey()).isEqualTo("envkey");
				assertThat(api.requireSteamId()).isEqualTo("76561197960287930");
			});
	}

	@org.junit.jupiter.params.ParameterizedTest
	@ValueSource(strings = { "steam.cache.owned-games-ttl=0s", "steam.cache.schema-ttl=8d", "steam.cache.news-ttl=500ms" })
	void binding_withCacheTtlOutOfRange_shouldFailStartup(String property) {
		// Given / When / Then
		runner.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	void binding_withoutCacheSettings_shouldUseSpecDefaults() {
		// Given / When
		runner.run(ctx -> {
			var cache = ctx.getBean(SteamProperties.class).cache();

			// Then
			assertThat(cache.enabled()).isTrue();
			assertThat(cache.ownedGamesTtl()).hasMinutes(5);
			assertThat(cache.recentGamesTtl()).hasMinutes(2);
			assertThat(cache.achievementsTtl()).hasMinutes(10);
			assertThat(cache.schemaTtl()).hasHours(24);
			assertThat(cache.newsTtl()).hasMinutes(10);
		});
	}

}
