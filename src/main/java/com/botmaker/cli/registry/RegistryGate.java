package com.botmaker.cli.registry;

import com.botmaker.cli.Console;
import com.botmaker.cli.Mvn;
import com.botmaker.cli.Subjects;
import com.botmaker.cli.validate.CheckResult;
import com.botmaker.cli.validate.PluginSubject;
import com.botmaker.cli.validate.PluginValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The registry's gate: the check that decides a pull request, run from the registry's own CI.
 *
 * <p><b>It is here, in {@code botmaker-cli}, and not in the registry repository, because it must be the
 * same code the author already ran.</b> A submission that fails for a reason its author could not have seen
 * coming is the experience the whole gate exists to prevent, so the registry's workflow resolves this
 * module's <em>main</em> artifact — the library, with no picocli in it — and calls this class. Everything it
 * adds on top of {@code botmaker validate} is what only the registry knows: the ids every other entry
 * already claims, and the rule that {@code index.json} is generated.
 *
 * <p><b>No command-line library, deliberately.</b> Two positional arguments, parsed by reading an array:
 * picocli is {@code optional} in this module precisely so a consumer resolving it as a library does not
 * download a parser, and a gate that needed one would undo that.
 *
 * <pre>
 * java -cp … com.botmaker.cli.registry.RegistryGate &lt;registry-root&gt; &lt;changed-path&gt;…
 * java -cp … com.botmaker.cli.registry.RegistryGate &lt;registry-root&gt; @changed.txt
 * </pre>
 *
 * <p>The {@code @file} form exists for a security reason rather than a convenience one: the changed paths
 * come from a pull request, so they are attacker-chosen text, and a filename containing a shell
 * metacharacter interpolated into a workflow's {@code run:} line is a command injection. Reading them from
 * a file, one per line, means no shell ever sees them.
 *
 * <p>Exit code: {@code 0} every changed entry passes, {@code 1} something failed, {@code 2} the invocation
 * itself was wrong. The same three the CLI uses, for the same reason — a workflow reads them.
 */
public final class RegistryGate {

    private RegistryGate() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        Console console = new Console(false);
        if (args.length < 2) {
            console.error("usage: RegistryGate <registry-root> <changed-path>...");
            return 2;
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        List<String> changed;
        try {
            changed = changedPaths(List.of(args).subList(1, args.length));
        } catch (IOException e) {
            console.error("could not read the changed-path list: " + e.getMessage());
            return 2;
        }

        // The generated file. Refused before anything is resolved, because a pull request that edits it has
        // misunderstood the layout rather than shipped a bad plugin, and telling it to go and download a jar
        // first would be telling it the wrong thing slowly.
        if (changed.stream().anyMatch(path -> path.equals(Registry.INDEX))) {
            console.error(Registry.INDEX + " is generated from " + Registry.ENTRIES_DIRECTORY
                    + "/*.json and committed by CI. Edit " + Registry.ENTRIES_DIRECTORY
                    + "/<plugin-id>.json instead — or let `botmaker publish` write it for you.");
            return 1;
        }

        List<String> entryPaths = changed.stream()
                .filter(path -> path.startsWith(Registry.ENTRIES_DIRECTORY + "/") && path.endsWith(".json"))
                .toList();
        if (entryPaths.isEmpty()) {
            console.out("No registry entries changed; nothing to validate.");
            return 0;
        }

        Registry registry;
        try {
            registry = Registry.read(root.resolve(Registry.ENTRIES_DIRECTORY));
        } catch (IOException e) {
            console.error("could not read " + Registry.ENTRIES_DIRECTORY + ": " + e.getMessage());
            return 1;
        }

        Subjects subjects = new Subjects(console, new Mvn(console));
        int failures = 0;
        for (String path : entryPaths) {
            Path file = root.resolve(path);
            if (!Files.isRegularFile(file)) {
                // A deletion. Removing a plugin from the index needs no artifact to resolve, and refusing
                // one would make an unmaintained plugin unremovable.
                console.out("removed: " + path);
                continue;
            }
            console.out("");
            console.out("── " + path);
            failures += validate(console, subjects, registry, file) ? 0 : 1;
        }

        console.out("");
        if (failures > 0) {
            console.out(failures + " entr" + (failures == 1 ? "y" : "ies") + " failed.");
            return 1;
        }
        console.out("All submitted entries pass.");
        return 0;
    }

    /** The paths as given, or — for a single {@code @file} argument — the lines of that file. */
    private static List<String> changedPaths(List<String> args) throws IOException {
        if (args.size() == 1 && args.getFirst().startsWith("@")) {
            return Files.readAllLines(Path.of(args.getFirst().substring(1))).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
        return args;
    }

    private static boolean validate(Console console, Subjects subjects, Registry registry, Path file) {
        Registry.Entry submitted;
        try {
            submitted = new Registry.Entry(file,
                    Registry.mapper().readValue(Files.readString(file), RegistryEntry.class));
        } catch (IOException e) {
            console.error("not valid JSON for a registry entry: " + e.getMessage());
            return false;
        }
        RegistryEntry entry = submitted.entry();

        // The layout's own check, and the reason it is worth having: git refuses a second file of the same
        // name, so a filename that equals the plugin's id makes id-uniqueness a property of the repository.
        // A file named after something else silently gives that away.
        if (!submitted.idFromFilename().equals(entry.id())) {
            console.error("the file is named " + submitted.idFromFilename() + ".json but the entry's id is "
                    + entry.id() + ". The filename IS the id: rename the file to " + entry.id() + ".json.");
            return false;
        }
        for (String required : List.of(entry.coordinate(), entry.verifiedVersion(), entry.repo())) {
            if (required == null || required.isBlank()) {
                console.error("coordinate, verifiedVersion and repo are all required; `botmaker publish`"
                        + " fills every one of them in.");
                return false;
            }
        }

        String coordinate = entry.coordinate() + ":" + entry.verifiedVersion();
        PluginSubject subject;
        try {
            subject = subjects.fromCoordinate(coordinate,
                    registry.claimedPluginIds(entry.id()),
                    registry.claimedValueTypeIds(entry.id()));
        } catch (IOException e) {
            console.error("could not resolve " + coordinate + ": " + e.getMessage());
            return false;
        }

        List<CheckResult> results = PluginValidator.validate(subject);
        console.report(results);
        if (!PluginValidator.passed(results)) {
            console.out(failedChecks(results));
            return false;
        }
        return true;
    }

    /**
     * The failing checks by id, on one line.
     *
     * <p>So the pull request's check list names what went wrong without anybody opening a log — which is the
     * difference between a contributor fixing their plugin and a contributor waiting for a maintainer.
     */
    private static String failedChecks(List<CheckResult> results) {
        return "FAILED: " + String.join(", ",
                results.stream().filter(CheckResult::failed).map(r -> r.check().id()).toList());
    }
}
