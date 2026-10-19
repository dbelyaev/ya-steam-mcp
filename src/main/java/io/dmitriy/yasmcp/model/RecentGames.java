package io.dmitriy.yasmcp.model;

import java.util.List;

public record RecentGames(String steamId, String windowDescription, List<RecentGame> games) {

	public static final String WINDOW = "Steam recent activity (typically last two weeks)";

}
