package io.dmitriy.yasmcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;

class SteamPropertiesTest {

	@Configuration
	@EnableConfigurationProperties(SteamProperties.class)
	static class Cfg {

	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Cfg.class);

	@Test
	void startsWithoutAnyCredentials() {
		runner.run(ctx -> {
			assertThat(ctx).hasNotFailed();
			var api = ctx.getBean(SteamProperties.class).api();
			assertThat(api.baseUrl()).hasToString("https://api.steampowered.com");
			assertThat(api.retries()).isEqualTo(2);
			assertThatThrownBy(api::requireKey).isInstanceOfSatisfying(SteamException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFIGURATION_MISSING));
			assertThatThrownBy(api::requireSteamId).isInstanceOfSatisfying(SteamException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFIGURATION_MISSING));
		});
	}

	@Test
	void malformedSteamIdStartsButFailsAtCallTimeWithoutEchoingValue() {
		runner.withPropertyValues("steam.api.steam-id=not-an-id-SECRET").run(ctx -> {
			assertThat(ctx).hasNotFailed();
			var api = ctx.getBean(SteamProperties.class).api();
			assertThatThrownBy(api::requireSteamId).isInstanceOf(SteamException.class)
				.hasMessageNotContaining("SECRET");
		});
	}

	@Test
	void validCredentialsAreReturnedUnchanged() {
		runner.withPropertyValues("steam.api.steam-id=76561197960287930", "steam.api.key=abc").run(ctx -> {
			var api = ctx.getBean(SteamProperties.class).api();
			assertThat(api.requireSteamId()).isEqualTo("76561197960287930");
			assertThat(api.requireKey()).isEqualTo("abc");
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://api.steampowered.com", "https://partner.steam-api.com",
			"https://api.steampowered.com.evil.example", "https://evil.example@api.steampowered.com",
			"https://api.steampowered.com/IPlayerService", "https://api.steampowered.com:8443",
			"http://localhost:8080", "http://127.0.0.1:8080" })
	void productionConfigRejectsOtherOrigins(String url) {
		runner.withPropertyValues("steam.api.base-url=" + url).run(ctx -> assertThat(ctx).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:8080", "http://127.0.0.1:9999", "https://localhost" })
	void testOverrideAllowsLoopbackOnly(String url) {
		runner.withPropertyValues("steam.api.base-url=" + url, "steam.api.allow-insecure-test-base-url=true")
			.run(ctx -> assertThat(ctx).hasNotFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://evil.example", "https://partner.steam-api.com", "http://localhost.evil.example" })
	void testOverrideStillRejectsNonLoopback(String url) {
		runner.withPropertyValues("steam.api.base-url=" + url, "steam.api.allow-insecure-test-base-url=true")
			.run(ctx -> assertThat(ctx).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "steam.api.retries=-1", "steam.api.retries=6", "steam.api.connect-timeout=0s",
			"steam.api.read-timeout=10m" })
	void rejectsUnsafeLimits(String property) {
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

}
