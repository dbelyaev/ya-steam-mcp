package io.dmitriy.yasmcp.support;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Turns untrusted Steam news markup (HTML, BBCode, Steam placeholders) into inert plain text.
 * Output is data for an agent, never instructions: markup is removed, scripts dropped, angle
 * bracket entities stay encoded, control and bidi-override characters are stripped.
 */
public final class NewsSanitizer {

	private static final Pattern SCRIPT_OR_STYLE_BLOCK = Pattern.compile("<(script|style)\\b.*?(</\\1\\s*>|$)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?(-->|$)", Pattern.DOTALL);

	private static final Pattern HTML_TAG = Pattern.compile("</?[a-zA-Z!?][^>]*(>|$)");

	private static final Pattern BBCODE_TAG = Pattern.compile("\\[/?[a-zA-Z*][^\\]\\n]{0,200}\\]");

	private static final Pattern STEAM_PLACEHOLDER = Pattern.compile("\\{STEAM_CLAN_(?:LOC_)?IMAGE}\\S*");

	private static final Pattern HIDDEN_CHARS = Pattern
		.compile("[\\p{Cc}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF&&[^\\n\\t]]");

	private static final Pattern BLANK_RUNS = Pattern.compile("[ \\t]+");

	private static final Pattern NEWLINE_RUNS = Pattern.compile("\\s*\\n\\s*");

	private static final String ELLIPSIS = "\u2026";

	private NewsSanitizer() {
	}

	/** @return plain text of at most {@code maxChars} characters (ellipsis included), empty when {@code maxChars} is 0 */
	public static String toPlainText(@Nullable String raw, int maxChars) {
		if (raw == null || maxChars <= 0) {
			return "";
		}
		String text = SCRIPT_OR_STYLE_BLOCK.matcher(raw).replaceAll(" ");
		text = HTML_COMMENT.matcher(text).replaceAll(" ");
		text = HTML_TAG.matcher(text).replaceAll(" ");
		text = BBCODE_TAG.matcher(text).replaceAll(" ");
		text = STEAM_PLACEHOLDER.matcher(text).replaceAll(" ");
		text = text.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
		text = HIDDEN_CHARS.matcher(text).replaceAll("");
		text = BLANK_RUNS.matcher(text).replaceAll(" ");
		text = NEWLINE_RUNS.matcher(text).replaceAll("\n").strip();
		return truncate(text, maxChars);
	}

	/** Only absolute https links with a host and no credentials are exposed; everything else is dropped. */
	public static @Nullable String safeHttpsUrl(@Nullable String url) {
		if (url == null || url.isBlank() || url.length() > 2048) {
			return null;
		}
		try {
			URI uri = new URI(url.trim());
			boolean ok = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
			return ok ? uri.toString() : null;
		}
		catch (URISyntaxException ex) {
			return null;
		}
	}

	private static String truncate(String text, int maxChars) {
		if (text.codePointCount(0, text.length()) <= maxChars) {
			return text;
		}
		int keep = Math.max(0, maxChars - ELLIPSIS.length());
		int end = text.offsetByCodePoints(0, Math.min(keep, text.codePointCount(0, text.length())));
		return text.substring(0, end).stripTrailing() + ELLIPSIS;
	}

}
