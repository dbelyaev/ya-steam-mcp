package io.dmitriy.yasmcp.model;

public final class StoreUrls {

	private StoreUrls() {
	}

	public static String app(long appid) {
		return "https://store.steampowered.com/app/" + appid + "/";
	}

}
