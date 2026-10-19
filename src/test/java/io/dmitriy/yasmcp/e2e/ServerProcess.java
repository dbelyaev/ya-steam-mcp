package io.dmitriy.yasmcp.e2e;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** The built jar as a child process: raw STDIO access, captured stdout/stderr, exit code. */
final class ServerProcess implements AutoCloseable {

	private final Process process;

	private final BufferedWriter stdin;

	private final List<String> stdout = Collections.synchronizedList(new ArrayList<>());

	private final List<String> stderr = Collections.synchronizedList(new ArrayList<>());

	private final Thread outReader;

	private final Thread errReader;

	private ServerProcess(Process process) {
		this.process = process;
		this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
		this.outReader = pump(process, false, stdout);
		this.errReader = pump(process, true, stderr);
	}

	static Path jar() {
		String path = System.getProperty("e2e.jar");
		if (path == null) {
			throw new IllegalStateException("system property e2e.jar is not set; run through failsafe (./mvnw verify)");
		}
		return Path.of(path);
	}

	static List<String> command() {
		return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-XX:+DisplayVMOutputToStderr", "-jar", jar().toString());
	}

	/** Starts the jar with exactly the given environment (inherited STEAM_* variables are removed). */
	static ServerProcess start(Map<String, String> env) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command());
		builder.environment().keySet().removeIf(name -> name.startsWith("STEAM_"));
		builder.environment().putAll(env);
		return new ServerProcess(builder.start());
	}

	private static Thread pump(Process process, boolean err, List<String> sink) {
		Thread thread = new Thread(() -> {
			try (var reader = new BufferedReader(new InputStreamReader(err ? process.getErrorStream() : process.getInputStream(),
					StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					sink.add(line);
				}
			}
			catch (IOException ignored) {
				// stream closed with the process
			}
		}, err ? "e2e-stderr" : "e2e-stdout");
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	void send(String jsonLine) throws IOException {
		stdin.write(jsonLine);
		stdin.newLine();
		stdin.flush();
	}

	void closeStdin() throws IOException {
		stdin.close();
	}

	/** @return exit code, or -1 if the process did not exit in time (it is then killed) */
	int awaitExit(Duration timeout) throws InterruptedException {
		if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
			process.destroyForcibly();
			return -1;
		}
		outReader.join(2000);
		errReader.join(2000);
		return process.exitValue();
	}

	boolean awaitStdoutLines(int count, Duration timeout) throws InterruptedException {
		long end = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < end) {
			if (stdout.size() >= count) {
				return true;
			}
			Thread.sleep(50);
		}
		return stdout.size() >= count;
	}

	List<String> stdoutLines() {
		synchronized (stdout) {
			return List.copyOf(stdout);
		}
	}

	String stderrText() {
		synchronized (stderr) {
			return String.join("\n", stderr);
		}
	}

	@Override
	public void close() {
		process.destroyForcibly();
	}

}
