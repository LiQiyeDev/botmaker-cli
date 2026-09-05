package com.botmaker.cli.release;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What Maven version a pinned plugin demands, read from that plugin's <b>own</b> pom.
 *
 * <p><b>A table would be a fourth copy of the same fact.</b> The prerequisite lives in the plugin's pom; a
 * list of known-bad versions here goes stale the first time somebody bumps a plugin it does not name, and
 * the failure it misses is permanent (see {@link JitpackPluginsGate}).
 *
 * <p>Poms are read from {@code ~/.m2/repository} when they are already there — they are, after any local
 * build — and from Maven Central otherwise. <b>A network failure is an unknown, never a refusal</b>: a gate
 * that cannot reach the internet must not be the reason a release stops.
 */
public final class MavenPrerequisite {

    private static final Pattern PLUGIN = Pattern.compile("<plugin>(.*?)</plugin>", Pattern.DOTALL);
    private static final Pattern PREREQUISITE =
            Pattern.compile("<prerequisites>.*?<maven>(.*?)</maven>", Pattern.DOTALL);
    private static final Pattern NUMERIC = Pattern.compile("^\\d+(\\.\\d+)*$");
    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    /**
     * One pinned plugin's verdict.
     *
     * @param needs   the Maven version it demands, empty when it declares none (which is the common case and
     *                not a finding)
     * @param unknown why it could not be answered, empty when it could
     */
    public record Pin(String artifactId, String version, Optional<String> needs, String unknown) {

        /** The script's {@code FAIL} line, without the marker. */
        public String failure() {
            return artifactId + ":" + version + " requires Maven " + needs.orElse("");
        }

        /** The script's {@code UNKNOWN} line, without the marker. */
        public String unknownLine() {
            return artifactId + ":" + version + " (" + unknown + ")";
        }
    }

    private MavenPrerequisite() {
    }

    /**
     * Every plugin a pom pins at a literal version, with what each demands.
     *
     * <p>A version that is still a property is skipped rather than resolved: the script does the same, and
     * the reason is that resolving it would mean interpolating the pom's own properties and its parent's,
     * which is a Maven model reader's job — and guessing wrong here refuses a release over a number nobody
     * pinned.
     */
    public static List<Pin> read(String pom, Path localRepository) {
        List<Pin> pins = new ArrayList<>();
        Matcher plugins = PLUGIN.matcher(pom);
        while (plugins.find()) {
            String block = plugins.group(1);
            String artifactId = tag(block, "artifactId");
            String version = tag(block, "version");
            if (artifactId.isEmpty() || version.isEmpty() || version.startsWith("${")) {
                continue;
            }
            String groupId = tag(block, "groupId");
            pins.add(prerequisiteOf(groupId.isEmpty() ? "org.apache.maven.plugins" : groupId,
                    artifactId, version, localRepository));
        }
        return List.copyOf(pins);
    }

    private static Pin prerequisiteOf(String groupId, String artifactId, String version, Path local) {
        String text;
        try {
            text = pluginPom(groupId, artifactId, version, local);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Pin(artifactId, version, Optional.empty(),
                    "could not read its pom: " + e.getMessage());
        }
        Matcher found = PREREQUISITE.matcher(text);
        if (!found.find()) {
            return new Pin(artifactId, version, Optional.empty(), "");   // declares none — not a finding
        }
        String value = found.group(1).strip();
        if (value.startsWith("${")) {
            // Resolved in that same pom, exactly as the script does: a property inherited from a parent
            // nobody publishes is the usual cause of an unknown, and is reported rather than guessed.
            value = tag(text, value.substring(2, value.length() - 1)).strip();
        }
        if (!NUMERIC.matcher(value).matches()) {
            return new Pin(artifactId, version, Optional.empty(),
                    "unresolved prerequisite '" + value + "'");
        }
        return new Pin(artifactId, version, Optional.of(value), "");
    }

    private static String pluginPom(String groupId, String artifactId, String version, Path local)
            throws IOException, InterruptedException {
        String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".pom";
        Path cached = local.resolve(path);
        if (Files.isRegularFile(cached)) {
            return Files.readString(cached);
        }
        try (HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20)).build()) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(CENTRAL + path))
                            .timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP Error " + response.statusCode());
            }
            return response.body();
        }
    }

    /** Whether a demanded version exceeds the ceiling, comparing part by part as numbers. */
    public static boolean exceeds(String needs, String ceiling) {
        String[] want = needs.split("\\.");
        String[] limit = ceiling.split("\\.");
        for (int i = 0; i < Math.max(want.length, limit.length); i++) {
            int a = i < want.length ? Integer.parseInt(want[i]) : 0;
            int b = i < limit.length ? Integer.parseInt(limit[i]) : 0;
            if (a != b) {
                return a > b;
            }
        }
        return false;
    }

    /** The default local repository; the gate takes it as a parameter so a test can hand it a temp dir. */
    public static Path localRepository() {
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    private static String tag(String xml, String name) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(name) + ">([^<]*)</"
                + Pattern.quote(name) + ">").matcher(xml);
        return matcher.find() ? matcher.group(1).strip() : "";
    }
}
