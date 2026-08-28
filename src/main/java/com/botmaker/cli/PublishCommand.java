package com.botmaker.cli;

import com.botmaker.cli.project.Poms;
import com.botmaker.cli.registry.RegistryEntry;
import com.botmaker.cli.validate.CheckResult;
import com.botmaker.cli.validate.PluginSubject;
import com.botmaker.cli.validate.PluginValidator;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.host.PluginLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code botmaker publish} — validate, then compose the registry entry and open the pull request.
 *
 * <p><b>The author never hand-edits {@code index.json}.</b> Almost every field in an entry is something the
 * plugin already says about itself — its id, its display name, the value type ids it registers, the contract
 * version its pom declares — and a human retyping those is a human introducing a typo into the one file that
 * is the registry's primary key. What is genuinely the author's, and only that, is asked for: the
 * description, the tags and the repository.
 *
 * <p>Validation runs first and a failure stops it. That is the whole point of {@code validate} being a
 * library rather than a command: the check that refuses the pull request is the check that refused to open
 * it.
 */
final class PublishCommand {

    private static final String REGISTRY_REPO = "LiQiyeDev/botmaker-plugin-registry";
    private static final String INDEX = "index.json";

    private final Console console;
    private final Subjects subjects;

    PublishCommand(Console console, Subjects subjects) {
        this.console = console;
        this.subjects = subjects;
    }

    int run(Args args) throws IOException {
        Path dir = Path.of(args.value("dir", ".")).toAbsolutePath().normalize();
        PluginSubject subject = subjects.fromDirectory(dir, !args.flag("no-build"));

        List<CheckResult> results = PluginValidator.validate(subject);
        console.report(results);
        if (!PluginValidator.passed(results)) {
            console.out("");
            console.error("not publishing: the plugin does not pass its own checks, and the registry runs"
                    + " these same checks on the pull request");
            return 1;
        }

        RegistryEntry entry = compose(args, subject);
        String json = mapper().writerWithDefaultPrettyPrinter().writeValueAsString(entry);

        if (args.flag("dry-run") || !args.has("repo")) {
            if (!args.has("repo")) {
                console.warn("--repo <owner/name> was not given, so this is a dry run: the registry entry"
                        + " needs somewhere for a reader to go and look at the source.");
            }
            console.out("");
            console.out(json);
            return 0;
        }
        return openPullRequest(entry, json);
    }

    private RegistryEntry compose(Args args, PluginSubject subject) throws IOException {
        String id = "";
        String name = "";
        List<String> valueTypeIds = new ArrayList<>();
        try (PluginLoader loaded =
                     PluginLoader.open(subject.classpath().stream().map(Object::toString).toList())) {
            // The validator has already refused an empty load, so there is at least one plugin here. The
            // FIRST is the entry's subject: a jar contributing two is legal and rare, and picking silently
            // would be worse than the warning.
            List<StudioPlugin> plugins = loaded == null ? List.of() : loaded.plugins();
            if (plugins.size() > 1) {
                console.warn("this jar contributes " + plugins.size() + " plugins; the entry describes"
                        + " the first, " + plugins.getFirst().id());
            }
            StudioPlugin plugin = plugins.getFirst();
            id = plugin.id();
            name = plugin.displayName();
            for (ValueType type : plugin.valueTypes().types()) {
                valueTypeIds.add(type.id());
            }
        }

        Poms.Dependency self = Poms.coordinate(subject.pom());
        String contractVersion = Poms.find(Poms.dependencies(subject.pom()),
                        "com.github.LiQiyeDev", "botmaker-studio-api")
                .map(Poms.Dependency::version)
                .orElse("");

        return new RegistryEntry(
                id,
                args.value("name", name),
                self.groupId() + ":" + self.artifactId(),
                args.value("repo", null),
                args.value("description", ""),
                List.of(args.value("tags", "").split("\\s*,\\s*")).stream().filter(t -> !t.isBlank()).toList(),
                args.value("min-contract-version", contractVersion),
                valueTypeIds,
                LocalDate.now().toString());
    }

    /**
     * Forks, branches, appends the entry and opens the PR — all through {@code gh}.
     *
     * <p>{@code gh} rather than the REST API directly, because it already holds the user's credential and
     * because a tool that asked a plugin author for a GitHub token would be asking for the one thing this
     * program has no business holding.
     */
    private int openPullRequest(RegistryEntry entry, String json) throws IOException {
        Path work = Files.createTempDirectory("botmaker-publish");
        Path clone = work.resolve("registry");
        if (gh(work, "repo", "fork", REGISTRY_REPO, "--clone=true", "--remote=false",
                "--fork-name=botmaker-plugin-registry") != 0) {
            console.error("could not fork " + REGISTRY_REPO + ". Is `gh` installed and authenticated?");
            return 1;
        }
        // `gh repo fork --clone` clones into the current directory under the repository's own name.
        Path cloned = work.resolve("botmaker-plugin-registry");
        if (Files.isDirectory(cloned)) {
            clone = cloned;
        }
        Path index = clone.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            console.error("no " + INDEX + " in " + REGISTRY_REPO + " — the registry is not published yet."
                    + " Re-run with --dry-run and open the pull request by hand.");
            return 1;
        }
        String branch = "add-" + entry.id().replaceAll("[^a-zA-Z0-9]+", "-");
        run(clone, "git", "checkout", "-b", branch);
        Files.writeString(index, merged(index, entry));
        run(clone, "git", "add", INDEX);
        run(clone, "git", "commit", "-m", "registry: add " + entry.id());
        if (run(clone, "git", "push", "-u", "origin", branch) != 0) {
            console.error("could not push the branch");
            return 1;
        }
        int opened = gh(clone, "pr", "create", "--repo", REGISTRY_REPO, "--title",
                "Add " + entry.name() + " (" + entry.id() + ")", "--body",
                "Adds `" + entry.id() + "` (`" + entry.coordinate() + "`).\n\n"
                        + "Composed by `botmaker publish`, which runs the same checks this repository's CI"
                        + " runs.\n\n```json\n" + json + "\n```");
        return opened == 0 ? 0 : 1;
    }

    /**
     * Appends the entry to the index, replacing any row that already holds its id.
     *
     * <p>Read, mutate, write as text rather than as a model: the file is somebody else's and a round trip
     * through a generic JSON binding would reformat every entry in it, turning a one-block diff into a
     * whole-file one that no reviewer can read.
     */
    private String merged(Path index, RegistryEntry entry) throws IOException {
        ObjectMapper mapper = mapper();
        List<RegistryEntry> entries = new ArrayList<>(List.of(
                mapper.readValue(Files.readString(index), RegistryEntry[].class)));
        entries.removeIf(existing -> existing.id().equals(entry.id()));
        entries.add(entry);
        entries.sort((a, b) -> a.id().compareTo(b.id()));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries) + "\n";
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    private int gh(Path dir, String... args) throws IOException {
        List<String> argv = new ArrayList<>(List.of("gh"));
        argv.addAll(List.of(args));
        return run(dir, argv.toArray(String[]::new));
    }

    private int run(Path dir, String... argv) throws IOException {
        console.step("$ " + String.join(" ", argv));
        try {
            return new ProcessBuilder(argv).directory(dir.toFile()).inheritIO().start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running " + argv[0], e);
        }
    }
}
