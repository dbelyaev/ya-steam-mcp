package io.dmitriy.yasteammcp.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/** WireMock standing in for api.steampowered.com. Appid 620 is owned and healthy, 10 has no stats, 500 fails upstream. */
final class FakeSteam implements AutoCloseable {

	static final String STEAM_ID = "76561197960287930";

	static final String API_KEY = "E2E-KEY-7c1d9a40beef";

	static final String BODY_SECRET = "E2E-UPSTREAM-BODY-SECRET";

	private final WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());

	FakeSteam start() {
		server.start();
		stubHealthy();
		return this;
	}

	WireMockServer server() {
		return server;
	}

	String baseUrl() {
		return "http://localhost:" + server.port();
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	private void stubHealthy() {
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/")).willReturn(json(200, """
				{"response":{"game_count":2,"games":[
				 {"appid":620,"name":"Portal 2","playtime_forever":740,"img_icon_url":"h"},
				 {"appid":10,"name":"Counter-Strike","playtime_forever":0}]}}""")));
		server.stubFor(get(urlPathEqualTo("/IPlayerService/GetRecentlyPlayedGames/v1/")).willReturn(json(200, """
				{"response":{"total_count":1,"games":[{"appid":620,"name":"Portal 2","playtime_2weeks":55,"playtime_forever":740}]}}""")));
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/")).willReturn(json(200, """
				{"playerstats":{"steamID":"x","gameName":"Portal 2","success":true,"achievements":[
				 {"apiname":"ACH_1","achieved":1,"unlocktime":1735732800},{"apiname":"ACH_2","achieved":0,"unlocktime":0}]}}""")));
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/")).withQueryParam("appid", equalTo("10"))
			.atPriority(1)
			.willReturn(json(400, "{\"playerstats\":{\"error\":\"Requested app has no stats\",\"success\":false}}")));
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetPlayerAchievements/v1/")).withQueryParam("appid", equalTo("500"))
			.atPriority(1)
			.willReturn(aResponse().withStatus(500).withBody(BODY_SECRET + API_KEY)));
		server.stubFor(get(urlPathEqualTo("/ISteamUserStats/GetSchemaForGame/v2/")).willReturn(json(200, """
				{"game":{"gameName":"Portal 2","availableGameStats":{"achievements":[
				 {"name":"ACH_1","displayName":"Wake Up Call","description":"Complete the first test","hidden":0},
				 {"name":"ACH_2","displayName":"Secret","description":"?","hidden":1}]}}}""")));
		server.stubFor(get(urlPathEqualTo("/ISteamNews/GetNewsForApp/v2/")).willReturn(json(200, """
				{"appnews":{"appid":620,"newsitems":[{"gid":"1","title":"<b>Big</b> update","url":"http://insecure.example/x",
				 "author":"Valve","contents":"[h1]Patch[/h1]<script>evil()</script> notes","date":1759314600}]}}""")));
	}

	@Override
	public void close() {
		server.stop();
	}

}
