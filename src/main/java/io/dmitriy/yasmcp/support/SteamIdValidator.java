package io.dmitriy.yasmcp.support;

import org.jspecify.annotations.Nullable;

import io.dmitriy.yasmcp.model.ErrorCode;
import io.dmitriy.yasmcp.model.SteamException;

/** SteamID64 is handled as a decimal string only; never via double or JSON number. */
public final class SteamIdValidator {

	private static final int LENGTH = 17;

	private SteamIdValidator() {
	}

	public static boolean isValid(@Nullable String value) {
		if (value == null || value.length() != LENGTH) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		try {
			Long.parseUnsignedLong(value);
		}
		catch (NumberFormatException _) {
			return false;
		}
		return true;
	}

	/** Returns the same string unchanged, or throws without echoing the value. */
	public static String requireValid(@Nullable String value) {
		if (value == null || !isValid(value)) {
			throw new SteamException(ErrorCode.INVALID_ARGUMENT, "SteamID64 must be a 17-digit decimal string");
		}
		return value;
	}

}
