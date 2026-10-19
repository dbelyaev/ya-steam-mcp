package io.dmitriy.yasmcp.steamdto;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SteamDtoDeserializationTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void ownedGames_withUnknownAndMissingFields_shouldDeserialize() {
		// Given
		String json = """
				{"response":{"game_count":2,"future_field":1,"games":[
				 {"appid":620,"name":"Portal 2","playtime_forever":740,"img_icon_url":"abc","new_thing":true},
				 {"appid":4294967295}]}}""";

		// When
		var r = mapper.readValue(json, OwnedGamesResponse.class);

		// Then
		var games = requireNonNull(requireNonNull(r.response()).games());
		assertThat(games).hasSize(2);
		assertThat(games.getFirst().playtimeForever()).isEqualTo(740);
		assertThat(games.get(1).appid()).isEqualTo(4294967295L);
		assertThat(games.get(1).name()).isNull();
	}

	@Test
	void ownedGames_whenResponseEmptyObject_shouldHaveNullGames() {
		// When
		var r = mapper.readValue("{\"response\":{}}", OwnedGamesResponse.class);

		// Then
		assertThat(requireNonNull(r.response()).games()).isNull();
	}

	@Test
	void ownedGames_whenEnvelopeMissing_shouldHaveNullResponse() {
		// When
		var r = mapper.readValue("{}", OwnedGamesResponse.class);

		// Then
		assertThat(r.response()).isNull();
	}

	@Test
	void recentlyPlayed_withGames_shouldMapPlaytime() {
		// Given
		String json = """
				{"response":{"total_count":1,"games":[{"appid":620,"name":"Portal 2","playtime_2weeks":55,"playtime_forever":740}]}}""";

		// When
		var r = mapper.readValue(json, RecentlyPlayedResponse.class);

		// Then
		var games = requireNonNull(requireNonNull(r.response()).games());
		assertThat(games.getFirst().playtime2weeks()).isEqualTo(55);
	}

	@Test
	void achievements_whenSuccessFalse_shouldExposeNoAchievements() {
		// Given
		String json = """
				{"playerstats":{"error":"Requested app has no stats","success":false}}""";

		// When
		var r = mapper.readValue(json, PlayerAchievementsResponse.class);

		// Then
		var stats = requireNonNull(r.playerstats());
		assertThat(stats.success()).isFalse();
		assertThat(stats.achievements()).isNull();
	}

	@Test
	void achievements_whenSuccess_shouldMapFields() {
		// Given
		String json = """
				{"playerstats":{"steamID":"76561197960287930","gameName":"X","success":true,
				 "achievements":[{"apiname":"ACH_1","achieved":1,"unlocktime":1735732800}]}}""";

		// When
		var r = mapper.readValue(json, PlayerAchievementsResponse.class);

		// Then
		var stats = requireNonNull(r.playerstats());
		assertThat(stats.steamID()).isEqualTo("76561197960287930");
		assertThat(requireNonNull(stats.achievements()).getFirst().unlocktime()).isEqualTo(1735732800L);
	}

	@Test
	void schema_withAchievements_shouldMapDisplayName() {
		// Given
		String json = """
				{"game":{"gameName":"X","availableGameStats":{"achievements":[
				 {"name":"ACH_1","displayName":"First","hidden":0,"description":"d","icon":"i"}]}}}""";

		// When
		var r = mapper.readValue(json, GameSchemaResponse.class);

		// Then
		var stats = requireNonNull(requireNonNull(r.game()).availableGameStats());
		assertThat(requireNonNull(stats.achievements()).getFirst().displayName()).isEqualTo("First");
	}

	@Test
	void news_withItem_shouldMapEpochDate() {
		// Given
		String json = """
				{"appnews":{"appid":620,"count":1,"newsitems":[
				 {"gid":"1","title":"T","url":"https://x","is_external_url":true,"author":"a","contents":"<b>c</b>","date":1759314600}]}}""";

		// When
		var r = mapper.readValue(json, NewsResponse.class);

		// Then
		var items = requireNonNull(requireNonNull(r.appnews()).newsitems());
		assertThat(items.getFirst().date()).isEqualTo(1759314600L);
	}

}
