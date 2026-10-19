package io.dmitriy.yasmcp.support;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;

/** AppID is uint32; held as long so the whole range is representable. */
public final class AppIdValidator {

	public static final long MAX = 4_294_967_295L;

	private AppIdValidator() {
	}

	public static long requireValid(Long appid) {
		if (appid == null || appid < 1 || appid > MAX) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT, "appid must be an integer between 1 and 4294967295");
		}
		return appid;
	}

}
