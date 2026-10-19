package io.dmitriy.yasmcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.dmitriy.yasmcp.config.SteamHttpConfig;
import io.dmitriy.yasmcp.config.SteamProperties;
import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import tools.jackson.databind.json.JsonMapper;

class SteamApiClientTest {

	private static final String KEY = "TESTKEY0123456789";

	private static final String STEAM_ID = "76561197960287930";

	private static final String BODY_SECRET = "SECRETBODYTEXT";

	private WireMockServer server;

	private final ResilientClients clients = new ResilientClients();

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	@BeforeEach
	void start() {
		server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		server.start();
		logs.start();
		((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
	}

	@AfterEach
	void stop() {
		((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
		// nothing but the five official paths may ever be requested
		server.getAllServeEvents().forEach(e -> assertThat(e.getRequest().getUrl())
			.matches("/(IPlayerService/GetOwnedGames/v1|IPlayerService/GetRecentlyPlayedGames/v1|ISteamUserStats/GetPlayerAchievements/v1|ISteamUserStats/GetSchemaForGame/v2|ISteamNews/GetNewsForApp/v2)/\\?.*"));
		// the key and upstream bodies never reach logs
		logs.list.forEach(ev -> {
			assertThat(ev.getFormattedMessage()).doesNotContain(KEY).doesNotContain(BODY_SECRET);
			assertThat(ev.getThrowableProxy()).isNull();
		});
		clients.close();
		server.stop();
	}

	private SteamApiClient client(int retries, Duration readTimeout, String key) {
		return client(retries, readTimeout, Duration.ofSeconds(30), key);
	}

	private SteamApiClient client(int retries, Duration readTimeout, Duration totalTimeout, String key) {
		var api = new SteamProperties.Api(URI.create("http://localhost:" + server.port()), key, STEAM_ID,
				Duration.ofSeconds(1), readTimeout, retries, Duration.ofMillis(1), totalTimeout, 4, "english", true);
		var props = new SteamProperties(api, new SteamProperties.Library(true), new SteamProperties.Store(false), SteamProperties.Cache.defaults());
		RestClient rc = SteamHttpConfig.customize(RestClient.builder(), api).build();
		SteamWebApi webApi = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rc)).build()
			.createClient(SteamWebApi.class);
		return clients.create(props, webApi, JsonMapper.builder().build());
	}

	private SteamApiClient client(int retries) {
		return client(retries, Duration.ofSeconds(2), KEY);
	}

	private static void assertCode(Throwable ex, ErrorCode code) {
		assertThat(ex).isInstanceOfSatisfying(SteamException.class, e -> {
			assertThat(e.code()).isEqualTo(code);
			assertThat(e.getCause()).isNull();
			assertThat(e.getMessage()).doesNotContain(KEY).doesNotContain(BODY_SECRET).doesNotContain("http");
		});
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	// ---- paths, versions, params ----

	@Test
	void getOwnedGames_withKeyAndSteamId_shouldCallPublicPathWithParams() {
		// Given
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/")).willReturn(json(200,
				"{\"response\":{\"game_count\":2,\"games\":[{\"appid\":620,\"name\":\"Portal 2\",\"playtime_forever\":740},{\"appid\":10}]}}")));

		// When
		var r = client(2).getOwnedGames();

		// Then
		var games = requireNonNull(requireNonNull(r.response()).games());
		assertThat(games).hasSize(2);
		assertThat(games.getFirst().playtimeForever()).isEqualTo(740);
		server.verify(1, getRequestedFor(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/"))
			.withQueryParam("key", equalTo(KEY))
			.withQueryParam("steamid", equalTo(STEAM_ID))
			.withQueryParam("include_appinfo", equalTo("true"))
			.withQueryParam("include_played_free_games", equalTo("true")));
	}

	@Test
	void getRecentlyPlayedGames_withCount_shouldSendCountParam() {
		// Given
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetRecentlyPlayedGames/v1/"))
			.willReturn(json(200, "{\"response\":{\"total_count\":0}}")));

		// When
		var r = client(2).getRecentlyPlayedGames(7);

		// Then
		assertThat(requireNonNull(r.response()).totalCount()).isZero();
		server.verify(getRequestedFor(urlPathEqualTo("/IPlayerService/GetRecentlyPlayedGames/v1/"))
			.withQueryParam("count", equalTo("7")).withQueryParam("steamid", equalTo(STEAM_ID)));
	}

	@Test
	void getAchievementsAndSchema_withAppid_shouldCallPathsWithAppidAndLanguage() {
		// Given
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/")).willReturn(json(200,
				"{\"playerstats\":{\"success\":true,\"achievements\":[{\"apiname\":\"A\",\"achieved\":1}]}}")));
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetSchemaForGame/v2/"))
			.willReturn(json(200, "{\"game\":{\"gameName\":\"X\"}}")));
		var c = client(2);

		// When
		var achievements = c.getPlayerAchievements(620, "english");
		var schema = c.getSchemaForGame(4294967295L, "russian");

		// Then
		assertThat(requireNonNull(requireNonNull(achievements.playerstats()).achievements())).hasSize(1);
		assertThat(requireNonNull(schema.game()).gameName()).isEqualTo("X");
		server.verify(getRequestedFor(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/"))
			.withQueryParam("appid", equalTo("620")).withQueryParam("l", equalTo("english"))
			.withQueryParam("steamid", equalTo(STEAM_ID)));
		server.verify(getRequestedFor(urlPathEqualTo("/ISteamUserStats/GetSchemaForGame/v2/"))
			.withQueryParam("appid", equalTo("4294967295")).withQueryParam("l", equalTo("russian"))
			.withQueryParam("key", equalTo(KEY)));
	}

	@Test
	void getNewsForApp_withoutKey_shouldOmitKeyAndHonorOptionalMaxlength() {
		// Given
		server.stubFor(get(urlPathEqualTo("/ISteamNews/GetNewsForApp/v2/"))
			.willReturn(json(200, "{\"appnews\":{\"appid\":620,\"newsitems\":[]}}")));
		var c = client(2, Duration.ofSeconds(2), "");

		// When
		c.getNewsForApp(620, 5, 1200);
		c.getNewsForApp(620, 5, null);

		// Then
		server.verify(1, getRequestedFor(urlPathEqualTo("/ISteamNews/GetNewsForApp/v2/"))
			.withQueryParam("maxlength", equalTo("1200")).withQueryParam("count", equalTo("5")));
		server.verify(1, getRequestedFor(urlPathEqualTo("/ISteamNews/GetNewsForApp/v2/"))
			.withoutQueryParam("maxlength").withoutQueryParam("key"));
	}

	// ---- configuration ----

	@Test
	void getOwnedGames_whenKeyMissing_shouldFailBeforeAnyRequest() {
		// Given
		var c = client(2, Duration.ofSeconds(2), "");

		// When / Then
		assertThatThrownBy(c::getOwnedGames).satisfies(ex -> assertCode(ex, ErrorCode.CONFIGURATION_MISSING));
		assertThat(server.getAllServeEvents()).isEmpty();
	}

	// ---- response shapes ----

	@Test
	void getOwnedGames_whenResponseEmpty_shouldReturnEmptyEnvelope() {
		// Given
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/")).willReturn(json(200, "{\"response\":{}}")));

		// When
		var r = client(2).getOwnedGames();

		// Then: an empty envelope is data, not proof of an empty library
		var body = requireNonNull(r.response());
		assertThat(body.games()).isNull();
	}

	@Test
	void getOwnedGames_whenEnvelopeMissing_shouldThrowBadResponse() {
		// Given
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/")).willReturn(json(200, "{}")));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_BAD_RESPONSE));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void getOwnedGames_whenJsonMalformed_shouldThrowBadResponseWithoutBody() {
		// Given
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/"))
			.willReturn(json(200, "{\"response\":" + BODY_SECRET)));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_BAD_RESPONSE));
	}

	@Test
	void getOwnedGames_whenBodyEmpty_shouldThrowBadResponse() {
		// Given
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/")).willReturn(aResponse().withStatus(200)));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_BAD_RESPONSE));
	}

	@Test
	void getPlayerAchievements_when400WithSteamBody_shouldReturnBodyToCaller() {
		// Given: Steam reports "no stats" as HTTP 400 with a JSON body
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/")).willReturn(json(400,
				"{\"playerstats\":{\"error\":\"Requested app has no stats\",\"success\":false}}")));

		// When
		var r = client(2).getPlayerAchievements(620, "english");

		// Then
		assertThat(requireNonNull(r.playerstats()).success()).isFalse();
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void getPlayerAchievements_when400WithHtml_shouldThrowBadResponse() {
		// Given
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/"))
			.willReturn(aResponse().withStatus(400).withBody("<html>" + BODY_SECRET + "</html>")));

		// When / Then
		assertThatThrownBy(() -> client(2).getPlayerAchievements(620, "english"))
			.satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_BAD_RESPONSE));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void getOwnedGames_whenRedirected_shouldNotFollowAndThrowBadResponse() {
		// Given
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/"))
			.willReturn(aResponse().withStatus(302).withHeader("Location", "/elsewhere")));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_BAD_RESPONSE));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	// ---- auth ----

	@Test
	void getOwnedGames_when401_shouldFailWithoutRetryOrBody() {
		// Given
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(401).withBody(BODY_SECRET + KEY)));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_AUTH_FAILED));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	@Test
	void getOwnedGames_when403_shouldMapToAuthFailed() {
		// Given
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(403).withBody(BODY_SECRET)));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_AUTH_FAILED));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	// ---- rate limit ----

	@Test
	void getOwnedGames_when429_shouldFailImmediatelyWithoutRetry() {
		// Given: Retry-After is deliberately ignored; the calling agent decides whether to retry
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "3")));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_RATE_LIMITED));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	// ---- 5xx ----

	@Test
	void getOwnedGames_whenServerErrorPersists_shouldRetryTwiceThenThrowUnavailable() {
		// Given
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(503).withBody(BODY_SECRET)));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_UNAVAILABLE));
		assertThat(server.getAllServeEvents()).hasSize(3);
	}

	@Test
	void getOwnedGames_whenServerErrorIsTransient_shouldSucceedOnRetry() {
		// Given
		server.stubFor(get(anyUrl()).inScenario("s").whenScenarioStateIs(Scenario.STARTED)
			.willReturn(aResponse().withStatus(500)).willSetStateTo("ok"));
		server.stubFor(get(anyUrl()).inScenario("s").whenScenarioStateIs("ok")
			.willReturn(json(200, "{\"response\":{\"games\":[]}}")));

		// When
		var r = client(2).getOwnedGames();

		// Then
		assertThat(requireNonNull(requireNonNull(r.response()).games())).isEmpty();
		assertThat(server.getAllServeEvents()).hasSize(2);
	}

	@Test
	void getOwnedGames_whenRetriesDisabled_shouldAttemptOnce() {
		// Given
		server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(500)));

		// When / Then
		assertThatThrownBy(() -> client(0).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_UNAVAILABLE));
		assertThat(server.getAllServeEvents()).hasSize(1);
	}

	// ---- network ----

	@Test
	void getOwnedGames_whenReadTimesOut_shouldRetryThenThrowUnavailable() {
		// Given
		server.stubFor(get(anyUrl()).willReturn(json(200, "{\"response\":{}}").withFixedDelay(1500)));

		// When / Then
		assertThatThrownBy(() -> client(1, Duration.ofMillis(300), KEY).getOwnedGames())
			.satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_UNAVAILABLE));
		assertThat(server.getAllServeEvents()).hasSize(2);
	}

	@Test
	void getOwnedGames_whenConnectionReset_shouldRetryThenThrowUnavailableWithoutUri() {
		// Given
		server.stubFor(get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		// When / Then
		assertThatThrownBy(() -> client(2).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_UNAVAILABLE));
		assertThat(server.getAllServeEvents()).hasSize(3);
	}

	@Test
	void getOwnedGames_whenTotalTimeoutIsShort_shouldStopRetryingEarly() {
		// Given: every attempt takes ~300ms, five retries allowed, but only 1s overall
		server.stubFor(get(anyUrl()).willReturn(json(200, "{\"response\":{}}").withFixedDelay(1500)));

		// When / Then
		assertThatThrownBy(() -> client(5, Duration.ofMillis(300), Duration.ofSeconds(1), KEY).getOwnedGames())
			.satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_UNAVAILABLE));
		assertThat(server.getAllServeEvents()).hasSizeLessThan(6);
	}

	@Test
	void getOwnedGames_whenEmptyResponseFault_shouldThrowUnavailable() {
		// Given
		server.stubFor(get(anyUrl()).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

		// When / Then
		assertThatThrownBy(() -> client(0).getOwnedGames()).satisfies(ex -> assertCode(ex, ErrorCode.UPSTREAM_UNAVAILABLE));
	}

}
