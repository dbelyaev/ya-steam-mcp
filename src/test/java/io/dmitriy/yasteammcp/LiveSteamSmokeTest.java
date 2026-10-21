package io.dmitriy.yasteammcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import io.dmitriy.yasteammcp.service.AchievementService;
import io.dmitriy.yasteammcp.service.LibraryService;
import io.dmitriy.yasteammcp.service.NewsService;
import io.dmitriy.yasteammcp.service.RecentGamesService;
import io.dmitriy.yasteammcp.model.ErrorCode;
import io.dmitriy.yasteammcp.model.SteamException;

/**
 * Optional smoke test against the real Steam Web API with your own key. Run with
 * {@code STEAM_API_KEY=... STEAM_ID=... ./mvnw -Plive test}. Excluded from the default build and
 * from CI. Assertions use counts, flags and error codes only, so a failure message cannot print
 * private library data; nothing is logged or stored.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "STEAM_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "STEAM_ID", matches = "\\d{17}")
@McpSpringBootTest
class LiveSteamSmokeTest {

	/** Team Fortress 2: free, public, has news and achievements. */
	private static final long PUBLIC_APPID = 440;

	@Autowired
	LibraryService library;

	@Autowired
	RecentGamesService recent;

	@Autowired
	NewsService news;

	@Autowired
	AchievementService achievements;

	@Test
	void getLibrary_withRealKey_shouldAnswerWithoutErrorOrReportUnknownVisibility() {
		// When
		var page = library.getLibrary(null, null, 0, 1, null).data();

		// Then
		boolean consistent = page.total() >= page.games().size();
		assertThat(consistent).isTrue();
		assertThat(page.visibilityStatus()).isNotNull();
	}

	@Test
	void getRecentGames_withRealKey_shouldAnswerWithoutError() {
		// When
		var games = recent.getRecentGames(1).data().games();

		// Then
		assertThat(games).hasSizeLessThanOrEqualTo(1);
	}

	@Test
	void getNews_forPublicApp_shouldReturnPlainTextWithoutMarkup() {
		// When
		var items = news.getNews(PUBLIC_APPID, 2, 200).data().items();

		// Then
		assertThat(items).hasSizeLessThanOrEqualTo(2);
		boolean markupFree = items.stream()
			.map(i -> i.contentPreview() == null ? "" : i.contentPreview())
			.noneMatch(text -> text.contains("<") || text.contains("[/") || text.length() > 200);
		assertThat(markupFree).isTrue();
		boolean httpsOnly = items.stream().allMatch(i -> i.url() == null || i.url().startsWith("https://"));
		assertThat(httpsOnly).isTrue();
	}

	@Test
	void getAchievements_forPublicApp_shouldSucceedOrReportAnAccessErrorNeverAnInternalOne() {
		// When
		ErrorCode outcome;
		try {
			achievements.getAchievements(PUBLIC_APPID, null, null, 5);
			outcome = null;
		}
		catch (SteamException ex) {
			outcome = ex.code();
		}

		// Then
		assertThat(outcome).isIn(null, ErrorCode.ACHIEVEMENTS_UNAVAILABLE, ErrorCode.DATA_NOT_ACCESSIBLE);
	}

}
