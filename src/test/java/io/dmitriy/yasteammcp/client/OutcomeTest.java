package io.dmitriy.yasteammcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.dmitriy.yasteammcp.client.SteamApiClientImpl.Outcome;

class OutcomeTest {

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void equals_withSameStatusAndEqualBodyContent_shouldBeEqualWithSameHash() {
		// Given
		var first = new Outcome(200, bytes("{}"));
		var second = new Outcome(200, bytes("{}"));

		// When / Then
		assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
	}

	@Test
	void equals_withDifferentStatusOrBody_shouldNotBeEqual() {
		// When / Then
		assertThat(new Outcome(200, bytes("a"))).isNotEqualTo(new Outcome(500, bytes("a")))
			.isNotEqualTo(new Outcome(200, bytes("b")))
			.isNotEqualTo(new Outcome(200, null));
		assertThat(new Outcome(-1, null)).isEqualTo(new Outcome(-1, null)).hasSameHashCodeAs(new Outcome(-1, null));
	}

	@Test
	void toString_withUntrustedBody_shouldReportLengthOnlyNeverContentOrIdentity() {
		// Given
		var outcome = new Outcome(401, bytes("UPSTREAM-SECRET-BODY"));

		// When
		String text = outcome.toString();

		// Then
		assertThat(text).isEqualTo("Outcome[status=401, body=20 bytes]")
			.doesNotContain("SECRET")
			.doesNotContain("[B@");
		assertThat(new Outcome(-1, null)).hasToString("Outcome[status=-1, body=none]");
	}

}
