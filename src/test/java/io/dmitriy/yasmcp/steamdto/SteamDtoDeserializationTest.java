package io.dmitriy.yasmcp.steamdto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SteamDtoDeserializationTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void ownedGames_withUnknownAndMissingFields_shouldDeserialize() {
		var r = mapper.readValue("""
				{"response":{"game_count":2,"future_field":1,"games":[
				 {"appid":620,"name":"Portal 2","playtime_forever":740,"img_icon_url":"abc","new_thing":true},
				 {"appid":4294967295}]}}""", OwnedGamesResponse.class);
		assertThat(r.response().games()).hasSize(2);
		assertThat(r.response().games().getFirst().playtimeForever()).isEqualTo(740);
		assertThat(r.response().games().get(1).appid()).isEqualTo(4294967295L);
		assertThat(r.response().games().get(1).name()).isNull();
	}

	@Test
	void ownedGames_whenResponseEmptyObject_shouldHaveNullGames() {
		var r = mapper.readValue("{\"response\":{}}", OwnedGamesResponse.class);
		assertThat(r.response()).isNotNull();
		assertThat(r.response().games()).isNull();
	}

	@Test
	void ownedGames_whenEnvelopeMissing_shouldHaveNullResponse() {
		assertThat(mapper.readValue("{}", OwnedGamesResponse.class).response()).isNull();
	}

	@Test
	void recentlyPlayed_withGames_shouldMapPlaytime() {
		var r = mapper.readValue("""
				{"response":{"total_count":1,"games":[{"appid":620,"name":"Portal 2","playtime_2weeks":55,"playtime_forever":740}]}}""",
				RecentlyPlayedResponse.class);
		assertThat(r.response().games().getFirst().playtime2weeks()).isEqualTo(55);
	}

	@Test
	void achievements_whenSuccessFalse_shouldExposeNoAchievements() {
		var r = mapper.readValue("""
				{"playerstats":{"error":"Requested app has no stats","success":false}}""",
				PlayerAchievementsResponse.class);
		assertThat(r.playerstats().success()).isFalse();
		assertThat(r.playerstats().achievements()).isNull();
	}

	@Test
	void achievements_whenSuccess_shouldMapFields() {
		var r = mapper.readValue("""
				{"playerstats":{"steamID":"76561197960287930","gameName":"X","success":true,
				 "achievements":[{"apiname":"ACH_1","achieved":1,"unlocktime":1735732800}]}}""",
				PlayerAchievementsResponse.class);
		assertThat(r.playerstats().steamID()).isEqualTo("76561197960287930");
		assertThat(r.playerstats().achievements().getFirst().unlocktime()).isEqualTo(1735732800L);
	}

	@Test
	void schema_withAchievements_shouldMapDisplayName() {
		var r = mapper.readValue("""
				{"game":{"gameName":"X","availableGameStats":{"achievements":[
				 {"name":"ACH_1","displayName":"First","hidden":0,"description":"d","icon":"i"}]}}}""",
				GameSchemaResponse.class);
		assertThat(r.game().availableGameStats().achievements().getFirst().displayName()).isEqualTo("First");
	}

	@Test
	void news_withItem_shouldMapEpochDate() {
		var r = mapper.readValue("""
				{"appnews":{"appid":620,"count":1,"newsitems":[
				 {"gid":"1","title":"T","url":"https://x","is_external_url":true,"author":"a","contents":"<b>c</b>","date":1759314600}]}}""",
				NewsResponse.class);
		assertThat(r.appnews().newsitems().getFirst().date()).isEqualTo(1759314600L);
	}

}
