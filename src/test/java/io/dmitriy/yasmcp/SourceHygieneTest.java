package io.dmitriy.yasmcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * stdout carries MCP JSON-RPC frames only. Any direct console output, stack-trace printing or
 * hard-coded secret-looking value in production code is a defect, so it is banned by test.
 */
class SourceHygieneTest {

	private static final Path MAIN = Path.of("src/main");

	private static final Pattern CONSOLE = Pattern.compile("System\\s*\\.\\s*(out|err)\\b|\\.printStackTrace\\s*\\(|\\bConsole\\b");

	private static List<Path> sources(String glob) throws IOException {
		try (Stream<Path> files = Files.walk(MAIN)) {
			return files.filter(p -> p.getFileName().toString().matches(glob)).toList();
		}
	}

	@Test
	void mainSources_shouldNotWriteToConsoleDirectly() throws IOException {
		// Given
		List<Path> java = sources(".*\\.java");

		// When
		List<String> offenders = java.stream().filter(p -> {
			try {
				return CONSOLE.matcher(Files.readString(p)).find();
			}
			catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		}).map(Path::toString).toList();

		// Then
		assertThat(java).isNotEmpty();
		assertThat(offenders).as("files writing to System.out/err or printing stack traces").isEmpty();
	}

	@Test
	void logbackConfig_shouldSendEverythingToStderrAndSilenceStatusOutput() throws IOException {
		// Given
		String xml = Files.readString(MAIN.resolve("resources/logback-spring.xml"));

		// Then
		assertThat(xml).contains("<target>System.err</target>");
		assertThat(xml).doesNotContain("System.out");
		assertThat(xml).contains("NopStatusListener");
	}

	@Test
	void applicationConfig_shouldDisableBannerAndNeverHardCodeCredentials() throws IOException {
		// Given
		String yml = Files.readString(MAIN.resolve("resources/application.yml"));

		// Then
		assertThat(yml).contains("banner-mode: \"off\"");
		assertThat(yml).contains("key: ${STEAM_API_KEY:}");
		assertThat(yml).contains("steam-id: ${STEAM_ID:}");
		assertThat(yml).doesNotContainPattern("(?i)key:\\s*[A-F0-9]{32}");
	}

}
