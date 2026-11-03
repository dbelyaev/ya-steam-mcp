package io.dmitriy.yasteammcp;

import java.util.List;

/** The MCP tools this server exposes, shared by every test that asserts the tool list. */
public final class TestTools {

	public static final List<String> NAMES = List.of("get_library", "get_game_details", "get_playtime",
			"get_achievements", "get_recent_games", "get_game_news");

	private TestTools() {
	}

}
