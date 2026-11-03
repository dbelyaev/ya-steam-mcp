package io.dmitriy.yasteammcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
		assertThat(page.total()).isGreaterThanOrEqualTo(page.games().size());
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
		assertThat(items).allSatisfy(item -> {
			assertThat(item.contentPreview() == null ? "" : item.contentPreview()).doesNotContain("<", "[/")
				.hasSizeLessThanOrEqualTo(200);
			assertThat(item.url() == null ? "https://" : item.url()).startsWith("https://");
		});
	}

	@Test
	void getAchievements_forPublicApp_shouldSucceedOrReportAnAccessErrorNeverAnInternalOne() {
		// When
		var failure = catchThrowableOfType(SteamException.class,
				() -> achievements.getAchievements(PUBLIC_APPID, null, null, 5));

		// Then: no exception at all is fine
		if (failure != null) {
			assertThat(failure.code()).isIn(ErrorCode.ACHIEVEMENTS_UNAVAILABLE, ErrorCode.DATA_NOT_ACCESSIBLE);
		}
	}

}
