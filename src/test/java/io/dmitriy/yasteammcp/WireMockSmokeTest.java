package io.dmitriy.yasteammcp;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

class WireMockSmokeTest {

	@Test
	void stub_underBootManagedJetty_shouldServeResponse() {
		// Given
		WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		server.start();
		try {
			server.stubFor(get(urlEqualTo("/ping")).willReturn(ok("pong")));

			// When
			String body = RestClient.create().get().uri("http://localhost:" + server.port() + "/ping")
					.retrieve().body(String.class);

			// Then
			assertThat(body).isEqualTo("pong");
		}
		finally {
			server.stop();
		}
	}

}
