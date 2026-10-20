package io.dmitriy.yasmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.dmitriy.yasmcp.client.SteamApiClient;
import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;
import io.dmitriy.yasmcp.steamdto.NewsResponse;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

	@Mock
	SteamApiClient client;

	NewsService service;

	@BeforeEach
	void setUp() {
		service = new NewsService(client);
	}

	private static NewsResponse news(NewsResponse.Item... items) {
		return new NewsResponse(new NewsResponse.AppNews(620L, List.of(items)));
	}

	private static NewsResponse.Item item(String title, String url, String contents) {
		return new NewsResponse.Item("gid-1", title, url, "<b>Author</b>", contents, 1759314600L);
	}

	@Test
	void getNews_withDefaults_shouldSanitizeTextAndMapFields() {
		// Given
		when(client.getNewsForApp(620, 5, 3600)).thenReturn(news(
				item("<i>Big</i> update", "https://store.steampowered.com/news/1", "[h1]Patch[/h1]<script>x()</script> notes &amp; fixes")));

		// When
		var items = service.getNews(620L, null, null).data().items();

		// Then
		var n = items.getFirst();
		assertThat(n.id()).isEqualTo("gid-1");
		assertThat(n.title()).isEqualTo("Big update");
		assertThat(n.url()).isEqualTo("https://store.steampowered.com/news/1");
		assertThat(n.publishedAt()).isEqualTo(Instant.parse("2025-10-01T10:30:00Z"));
		assertThat(n.author()).isEqualTo("Author");
		assertThat(n.contentPreview()).isEqualTo("Patch notes & fixes");
	}

	@Test
	void getNews_withNonHttpsLink_shouldDropTheUrl() {
		// Given
		when(client.getNewsForApp(620, 5, 3600)).thenReturn(news(item("t", "http://example.com/a", "c"),
				item("t2", "javascript:alert(1)", "c")));

		// When
		var items = service.getNews(620L, null, null).data().items();

		// Then
		assertThat(items).extracting(i -> i.url()).containsOnlyNulls();
	}

	@Test
	void getNews_withZeroMaxContentChars_shouldOmitBodyAndAskSteamForMinimalLength() {
		// Given: Steam treats maxlength 0 as full content, so the service must not send 0 or omit it
		when(client.getNewsForApp(620, 5, 1)).thenReturn(news(item("t", "https://x.example/a", "very long article body")));

		// When
		var items = service.getNews(620L, null, 0).data().items();

		// Then
		assertThat(items.getFirst().contentPreview()).isNull();
		assertThat(items.getFirst().title()).isEqualTo("t");
		verify(client).getNewsForApp(620, 5, 1);
	}

	@Test
	void getNews_withMaxContentChars_shouldBoundPreviewAndRawRequest() {
		// Given
		when(client.getNewsForApp(620, 2, 300)).thenReturn(news(item("t", "https://x.example/a", "y".repeat(500))));

		// When
		var preview = service.getNews(620L, 2, 100).data().items().getFirst().contentPreview();

		// Then
		assertThat(preview).hasSize(100).endsWith("\u2026");
	}

	@Test
	void getNews_withMaxContentCharsAtCeiling_shouldCapRawRequest() {
		// Given
		when(client.getNewsForApp(620, 5, 12_000)).thenReturn(news());

		// When
		var data = service.getNews(620L, null, 4000).data();

		// Then
		assertThat(data.items()).isEmpty();
	}

	@Test
	void getNews_whenSteamReturnsMoreThanLimit_shouldTruncateLocally() {
		// Given
		when(client.getNewsForApp(620, 1, 3600)).thenReturn(news(item("a", null, "c"), item("b", null, "c")));

		// When
		var items = service.getNews(620L, 1, null).data().items();

		// Then
		assertThat(items).hasSize(1);
	}

	@Test
	void getNews_withMissingFields_shouldMapToNulls() {
		// Given
		when(client.getNewsForApp(620, 5, 3600)).thenReturn(news(new NewsResponse.Item(null, null, null, null, null, null)));

		// When
		var n = service.getNews(620L, null, null).data().items().getFirst();

		// Then
		assertThat(n.id()).isNull();
		assertThat(n.title()).isNull();
		assertThat(n.url()).isNull();
		assertThat(n.publishedAt()).isNull();
		assertThat(n.author()).isNull();
		assertThat(n.contentPreview()).isNull();
	}

	@Test
	void getNews_whenNoNewsItems_shouldReturnEmptyList() {
		// Given
		when(client.getNewsForApp(620, 5, 3600)).thenReturn(new NewsResponse(new NewsResponse.AppNews(620L, null)));

		// When / Then
		assertThat(service.getNews(620L, null, null).data().items()).isEmpty();
	}

	@Test
	void getNews_whenUpstreamFails_shouldPropagateRetryableError() {
		// Given
		when(client.getNewsForApp(620, 5, 3600)).thenThrow(new io.dmitriy.yasmcp.model.TransientUpstreamException());

		// When / Then
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getNews(620L, null, null))
			.isInstanceOfSatisfying(SteamException.class, ex -> assertThat(ex.retryable()).isTrue());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = { 0, -1, 4294967296L })
	void getNews_withInvalidAppid_shouldThrowInvalidArgumentWithoutUpstreamCall(Long appid) {
		// When / Then
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getNews(appid, null, null))
			.isInstanceOfSatisfying(SteamException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		verifyNoInteractions(client);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 21 })
	void getNews_withInvalidLimit_shouldThrowInvalidArgument(int limit) {
		// When / Then
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getNews(620L, limit, null))
			.isInstanceOfSatisfying(SteamException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		verifyNoInteractions(client);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 4001 })
	void getNews_withInvalidMaxContentChars_shouldThrowInvalidArgument(int chars) {
		// When / Then
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getNews(620L, null, chars))
			.isInstanceOfSatisfying(SteamException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
		verify(client, never()).getNewsForApp(org.mockito.ArgumentMatchers.anyLong(), anyInt(),
				org.mockito.ArgumentMatchers.any());
	}

}
