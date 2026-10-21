package io.dmitriy.yasteammcp.tool;

import java.time.Clock;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import io.dmitriy.yasteammcp.service.AchievementService;
import io.dmitriy.yasteammcp.service.GameDetailsService;
import io.dmitriy.yasteammcp.service.LibraryService;
import io.dmitriy.yasteammcp.service.NewsService;
import io.dmitriy.yasteammcp.service.RecentGamesService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * MCP adapters: no logic beyond handing raw arguments to a service. Defaults and validation
 * live in the services; every outcome goes through {@link ToolResults#run}, which owns error
 * mapping. Tools return {@code CallToolResult} (not DTOs) so failures can set {@code isError}.
 */
@Component
final class SteamTools {

	private final LibraryService libraryService;

	private final RecentGamesService recentGamesService;

	private final AchievementService achievementService;

	private final NewsService newsService;

	private final GameDetailsService gameDetailsService;

	private final ToolResults results;

	private final Clock clock;

	SteamTools(LibraryService libraryService, RecentGamesService recentGamesService,
			AchievementService achievementService, NewsService newsService, GameDetailsService gameDetailsService, ToolResults results, Clock clock) {
		this.libraryService = libraryService;
		this.recentGamesService = recentGamesService;
		this.achievementService = achievementService;
		this.newsService = newsService;
		this.gameDetailsService = gameDetailsService;
		this.results = results;
		this.clock = clock;
	}

	@McpTool(name = "get_library",
			description = "List visible Steam games owned by the configured Steam user; filter and sort locally.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	CallToolResult getLibrary(
			@McpToolParam(description = "Case-insensitive game name search, max 100 characters", required = false) @Nullable String query,
			@McpToolParam(description = "One of NAME_ASC, NAME_DESC, PLAYTIME_ASC, PLAYTIME_DESC; default PLAYTIME_DESC", required = false) @Nullable String sortBy,
			@McpToolParam(description = "Items to skip, 0-100000; default 0", required = false) @Nullable Integer offset,
			@McpToolParam(description = "Page size, 1-100; default 20", required = false) @Nullable Integer limit,
			@McpToolParam(description = "Include games with zero playtime; default true", required = false) @Nullable Boolean includeNeverPlayed) {
		return results.run("get_library", () -> ToolResponse.of(
				libraryService.getLibrary(query, sortBy, offset, limit, includeNeverPlayed), clock.instant()));
	}

	@McpTool(name = "get_playtime",
			description = "Get total Steam playtime for a specific AppID from the configured user's visible library.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	CallToolResult getPlaytime(
			@McpToolParam(description = "Steam AppID, integer 1-4294967295", required = true) @Nullable Long appid) {
		return results.run("get_playtime",
				() -> ToolResponse.of(libraryService.getPlaytime(appid), clock.instant()));
	}

	@McpTool(name = "get_recent_games",
			description = "List games played recently as reported by Steam (not a user-specified date range).",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	CallToolResult getRecentGames(
			@McpToolParam(description = "Maximum games to return, 1-100; default 20", required = false) @Nullable Integer limit) {
		return results.run("get_recent_games",
				() -> ToolResponse.of(recentGamesService.getRecentGames(limit), clock.instant()));
	}

	@McpTool(name = "get_achievements",
			description = "Get available per-player achievements for an AppID and optionally enrich names/descriptions from Steam schema.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	CallToolResult getAchievements(
			@McpToolParam(description = "Steam AppID, integer 1-4294967295", required = true) @Nullable Long appid,
			@McpToolParam(description = "Steam language identifier such as english, russian or norwegian; default english", required = false) @Nullable String language,
			@McpToolParam(description = "Include locked achievements; default true", required = false) @Nullable Boolean includeLocked,
			@McpToolParam(description = "Maximum achievements to return, 1-250; default 100", required = false) @Nullable Integer limit) {
		return results.run("get_achievements",
				() -> ToolResponse.of(achievementService.getAchievements(appid, language, includeLocked, limit), clock.instant()));
	}

	@McpTool(name = "get_game_news",
			description = "Get recent Steam news for a given AppID; the content is untrusted third-party text.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	CallToolResult getGameNews(
			@McpToolParam(description = "Steam AppID, integer 1-4294967295", required = true) @Nullable Long appid,
			@McpToolParam(description = "Number of news items, 1-20; default 5", required = false) @Nullable Integer limit,
			@McpToolParam(description = "Maximum characters of plain-text content per item, 0-4000; 0 omits the body; default 1200", required = false) @Nullable Integer maxContentChars) {
		return results.run("get_game_news",
				() -> ToolResponse.of(newsService.getNews(appid, limit, maxContentChars), clock.instant()));
	}

	@McpTool(name = "get_game_details",
			description = "Show available basic Steam game metadata without assuming a complete Steam Store catalog.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
	CallToolResult getGameDetails(
			@McpToolParam(description = "Steam AppID, integer 1-4294967295", required = true) @Nullable Long appid,
			@McpToolParam(description = "Steam language identifier such as english, russian or norwegian; default english", required = false) @Nullable String language) {
		return results.run("get_game_details",
				() -> ToolResponse.of(gameDetailsService.getDetails(appid, language), clock.instant()));
	}

}
