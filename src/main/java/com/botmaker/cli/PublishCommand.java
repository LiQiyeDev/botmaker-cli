package com.botmaker.cli;

import com.botmaker.cli.project.Poms;
import com.botmaker.cli.project.Tags;
import com.botmaker.cli.registry.Registry;
import com.botmaker.cli.registry.RegistryEntry;
import com.botmaker.cli.validate.CheckResult;
import com.botmaker.cli.validate.PluginSubject;
import com.botmaker.cli.validate.PluginValidator;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.host.PluginLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@code botmaker publish} — validate, then compose the registry entry and open the pull request.
 *
 * <p><b>The author never hand-edits anything in the registry.</b> Almost every field in an entry is something the
 * plugin already says about itself — its id, its display name, the value type ids it registers, the contract
 * version its pom declares — and a human retyping those is a human introducing a typo into the one file that
 * is the registry's primary key. What is genuinely the author's, and only that, is asked for: the
 * description, the tags and the repository.
 *
 * <p>Validation runs first and a failure stops it. That is the whole point of {@code validate} being a
 * library rather than a command: the check that refuses the pull request is the check that refused to open
 * it.
 *
 * <p><b>Nothing is published that nobody has followed.</b> An entry is a set of pointers — a coordinate at a
 * version, and a repository — and every one of them is checked here before the pull request is opened, for
 * the same reason validation runs here at all: a submission that fails in the registry's CI for a reason its
 * author could not have seen coming is the experience this command exists to prevent. So the version is a
 * <b>git tag</b> rather than the pom's {@code <version>} (see {@link #publishedVersion}), a
 * {@code -SNAPSHOT} is refused by name, the coordinate is resolved before the pull request is opened, and
 * {@code --repo} is confirmed to exist.
 *
 * <p><b>One file, {@code plugins/&lt;plugin-id&gt;.json}, and never the index.</b> The index is generated
 * from those files by the registry's own CI. That is what makes two authors publishing on the same day two
 * pull requests with no line in common — an entry appended to a shared array conflicts with every other
 * submission, and is a read-modify-write race against a base SHA somebody else may have moved.
 */
@Command(name = "publish",
        header = "Validate, then open the registry pull request.",
        description = "Almost every field of an entry is something the plugin already says about itself; "
                + "what is genuinely yours — the repository, the description, the tags — is asked for here.",
        mixinStandardHelpOptions = true)
final class PublishCommand implements Callable<Integer> {

    private static final String REGISTRY_REPO = "LiQiyeDev/botmaker-plugin-registry";

    @ParentCommand
    private Main parent;

    @Option(names = "--dir", defaultValue = ".",
            description = "The plugin project. Default: ${DEFAULT-VALUE}")
    private String directory;

    @Option(names = "--repo", paramLabel = "<owner/name>",
            description = "Where the plugin's source lives. Required for a real run; without it this is a "
                    + "dry run, because an entry needs somewhere for a reader to go and look.")
    private String repo;

    @Option(names = "--name", description = "Overrides the plugin's own displayName.")
    private String displayName;

    @Option(names = "--description", defaultValue = "", description = "One sentence, for the index.")
    private String description;

    @Option(names = "--tags", defaultValue = "", paramLabel = "<a,b,c>", description = "Comma separated.")
    private String tags;

    /**
     * <b>{@code --tag}, not {@code --version}, and the name is forced rather than chosen.</b>
     * {@code mixinStandardHelpOptions} already declares {@code -V, --version} on every command here, so a
     * second one would be a duplicate picocli refuses at construction. It reads better anyway: what JitPack
     * serves an artifact under is a git tag.
     */
    @Option(names = "--tag", paramLabel = "<tag>",
            description = "The published version the registry's gate will resolve. Default: the newest git "
                    + "tag on this working copy, or the pom's own <version> when there is not one.")
    private String tag;

    @Option(names = "--min-contract-version", paramLabel = "<version>",
            description = "Default: the botmaker-studio-api version this pom declares.")
    private String minContractVersion;

    @Option(names = "--dry-run", description = "Print the index entry and open nothing.")
    private boolean dryRun;

    @Option(names = "--no-build", description = "Trust the existing target/classes.")
    private boolean noBuild;

    @Override
    public Integer call() throws IOException {
        Console console = parent.console();
        Path dir = Path.of(directory).toAbsolutePath().normalize();
        Path pom = dir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            throw new IOException("no pom.xml in " + dir + " — point `botmaker publish` at a plugin project");
        }

        // Before the compile, because both of these are refusals and a refusal the author waits a minute for
        // is a refusal that reads as a fault in the tool.
        String published = publishedVersion(dir, pom);
        String snapshot = snapshotRefusal(published);
        if (snapshot != null) {
            console.error(snapshot);
            return 1;
        }
        if (repo != null && !dryRun && !repositoryExists(repo)) {
            console.error("no repository " + repo + " that `gh` can see. An entry's repo is the one field"
                    + " whose whole job is to give a reader somewhere to go.");
            return 1;
        }

        PluginSubject subject = parent.subjects().fromDirectory(dir, !noBuild);

        // Aside, on stderr: this command's stdout is the entry, and `--dry-run > entry.json` has to be a
        // file a parser will read.
        List<CheckResult> results = PluginValidator.validate(subject);
        console.reportAside(results);
        if (!PluginValidator.passed(results)) {
            console.error("not publishing: the plugin does not pass its own checks, and the registry runs"
                    + " these same checks on the pull request");
            return 1;
        }

        RegistryEntry entry = compose(subject, published);
        String json = Registry.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(entry);

        if (dryRun || repo == null) {
            if (repo == null) {
                console.warn("--repo <owner/name> was not given, so this is a dry run: the registry entry"
                        + " needs somewhere for a reader to go and look at the source.");
            }
            // Nothing but the entry on stdout: `botmaker publish --dry-run > plugins/<id>.json` is the
            // by-hand path when `gh` is unavailable, and a blank line makes that file not-JSON.
            console.out(json);
            return 0;
        }
        if (!resolves(entry)) {
            return 1;
        }
        return openPullRequest(entry, json);
    }

    /**
     * The version the entry publishes, which is the version the registry's gate resolves from JitPack.
     *
     * <p><b>The newest git tag, not the pom's {@code <version>}, and that is a correction rather than a
     * preference.</b> JitPack builds a tag on demand and serves the result under that tag whatever the pom
     * says — this project's own poms carry a cosmetic {@code 0.0.0-SNAPSHOT} for exactly that reason — so a
     * pom version resolves only where it happens to equal the tag. {@code --tag} overrides both, for the
     * author whose newest tag is not what they publish under.
     */
    private String publishedVersion(Path dir, Path pom) throws IOException {
        if (tag != null && !tag.isBlank()) {
            return tag.trim();
        }
        Optional<String> tag = Tags.newest(dir);
        if (tag.isPresent()) {
            return tag.get();
        }
        return Poms.coordinate(pom).version();
    }

    /**
     * A snapshot is refused here rather than left to the gate, because the point is that the author sees it.
     *
     * <p>{@code botmaker new} generates {@code 0.1.0-SNAPSHOT}, so publishing straight after generating is
     * the very first thing a new author does and produced an entry whose gate failed in the <em>registry's</em>
     * CI. Returns the refusal, or {@code null} when there is nothing to refuse.
     */
    static String snapshotRefusal(String published) {
        if (published == null || published.isBlank()) {
            return "no version to publish: this working copy has no git tag and its pom declares no"
                    + " <version>. Tag a release, or pass --tag <tag>.";
        }
        if (published.endsWith("-SNAPSHOT")) {
            return "the version to publish is " + published + ", and JitPack cannot resolve a snapshot:"
                    + " the registry's gate would download nothing. Tag a release (`git tag v0.1.0 && git"
                    + " push --tags`) and re-run, or pass --tag <tag>.";
        }
        return null;
    }

    /**
     * {@code gh repo view}, which is the cheapest possible statement of "this pointer points at something".
     *
     * <p>Only on a real run: {@code --dry-run} is the by-hand path taken when {@code gh} is unavailable, and
     * a check that needs {@code gh} cannot be the thing that stops it.
     */
    private boolean repositoryExists(String ownerAndName) throws IOException {
        Path here = Path.of(".").toAbsolutePath().normalize();
        return gh(here, "repo", "view", ownerAndName, "--json", "name") == 0;
    }

    /**
     * The gate's own first step, run locally.
     *
     * <p>{@link Subjects#fromCoordinate} is literally what {@code RegistryGate} calls, so a coordinate that
     * will not resolve here is a pull request that cannot pass — and finding that out costs seconds where
     * finding it out in CI costs a round trip. Not the full validation again: the checks have already run
     * against this working copy, and what is being asked is only whether the world can download what the
     * entry names.
     */
    private boolean resolves(RegistryEntry entry) {
        Console console = parent.console();
        String coordinate = entry.coordinate() + ":" + entry.verifiedVersion();
        try {
            parent.subjects().fromCoordinate(coordinate, Set.of(), Set.of());
            return true;
        } catch (IOException e) {
            console.error("nobody can download " + coordinate + " yet, so the registry's gate could not"
                    + " check it either:\n" + e.getMessage()
                    + "\nPush the tag and let JitPack build it, then re-run.");
            return false;
        }
    }

    private RegistryEntry compose(PluginSubject subject, String published) throws IOException {
        Console console = parent.console();
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
                displayName != null ? displayName : name,
                self.groupId() + ":" + self.artifactId(),
                repo,
                description,
                List.of(tags.split("\\s*,\\s*")).stream().filter(t -> !t.isBlank()).toList(),
                minContractVersion != null ? minContractVersion : contractVersion,
                valueTypeIds,
                // The tag the world can download, which is the version the registry's gate will resolve and
                // run the same checks against. A verifiedAt with no version beside it is a date attached to
                // no artifact — and the pom's own <version>, which stood here until 2026-09-04, is a date
                // attached to an artifact only JitPack's tag happens to agree with.
                published,
                LocalDate.now().toString());
    }

    /**
     * Forks, branches, writes the entry file and opens the PR — all through {@code gh}.
     *
     * <p>{@code gh} rather than the REST API directly, because it already holds the user's credential and
     * because a tool that asked a plugin author for a GitHub token would be asking for the one thing this
     * program has no business holding.
     */
    private int openPullRequest(RegistryEntry entry, String json) throws IOException {
        Console console = parent.console();
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
        Path entries = clone.resolve(Registry.ENTRIES_DIRECTORY);
        if (!Files.isDirectory(entries)) {
            console.error("no " + Registry.ENTRIES_DIRECTORY + "/ in " + REGISTRY_REPO + " — the registry is"
                    + " not published yet. Re-run with --dry-run and open the pull request by hand.");
            return 1;
        }
        String branch = "add-" + entry.id().replaceAll("[^a-zA-Z0-9]+", "-");
        run(clone, "git", "checkout", "-b", branch);
        // The filename is the id, so an update rewrites this author's own file and a new plugin adds one:
        // either way the diff touches nothing anybody else's submission touches.
        String file = Registry.ENTRIES_DIRECTORY + "/" + entry.id() + ".json";
        Files.writeString(entries.resolve(entry.id() + ".json"), json + "\n");
        run(clone, "git", "add", file);
        run(clone, "git", "commit", "-m", "registry: add " + entry.id());
        if (run(clone, "git", "push", "-u", "origin", branch) != 0) {
            console.error("could not push the branch");
            return 1;
        }
        int opened = gh(clone, "pr", "create", "--repo", REGISTRY_REPO, "--title",
                "Add " + entry.name() + " (" + entry.id() + ")", "--body",
                "Adds `" + Registry.ENTRIES_DIRECTORY + "/" + entry.id() + ".json` — `"
                        + entry.coordinate() + ":" + entry.verifiedVersion() + "`.\n\n"
                        + "Composed by `botmaker publish`, which runs the same checks this repository's CI"
                        + " runs.\n\n```json\n" + json + "\n```");
        return opened == 0 ? 0 : 1;
    }

    private int gh(Path dir, String... args) throws IOException {
        List<String> argv = new ArrayList<>(List.of("gh"));
        argv.addAll(List.of(args));
        return run(dir, argv.toArray(String[]::new));
    }

    private int run(Path dir, String... argv) throws IOException {
        parent.console().step("$ " + String.join(" ", argv));
        try {
            return new ProcessBuilder(argv).directory(dir.toFile()).inheritIO().start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running " + argv[0], e);
        }
    }
}
