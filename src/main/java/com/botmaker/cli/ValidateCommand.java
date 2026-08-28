package com.botmaker.cli;

import com.botmaker.cli.validate.CheckResult;
import com.botmaker.cli.validate.PluginSubject;
import com.botmaker.cli.validate.PluginValidator;
import com.botmaker.cli.validate.Status;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * {@code botmaker validate} — the seven checks, against a working copy or a published coordinate.
 *
 * <p>This class is a <em>front end</em>: it resolves a subject, calls
 * {@link PluginValidator#validate(PluginSubject)} and prints. Every rule it appears to enforce lives in that
 * library, because the registry's CI calls the library directly and a rule implemented here would be a rule
 * the author sees and the gate does not.
 */
final class ValidateCommand {

    private final Console console;
    private final Subjects subjects;

    ValidateCommand(Console console, Subjects subjects) {
        this.console = console;
        this.subjects = subjects;
    }

    int run(Args args) throws IOException {
        PluginSubject subject;
        String coordinate = args.value("coordinate", null);
        if (coordinate != null) {
            subject = subjects.fromCoordinate(coordinate, Set.of(), Set.of());
        } else {
            Path dir = Path.of(args.at(1) == null ? args.value("dir", ".") : args.at(1))
                    .toAbsolutePath().normalize();
            subject = subjects.fromDirectory(dir, !args.flag("no-build"));
        }

        List<CheckResult> results = PluginValidator.validate(subject);
        console.report(results);

        boolean passed = PluginValidator.passed(results);
        console.out("");
        if (!passed) {
            console.out(results.stream().filter(CheckResult::failed).count() + " check(s) failed.");
            return 1;
        }
        // Said on every pass, and not as a formality. Two of the seven checks ask whether an id is already
        // claimed, and locally nothing is claimed — the registry's index is what holds those answers. An
        // author who reads "all checks passed" as "the PR will be accepted" has been misled by this tool.
        console.out("All checks passed"
                + (results.stream().anyMatch(r -> r.status() == Status.SKIP) ? " (some were skipped)." : ".")
                + " Collisions with plugins already in the registry are checked by the registry's own CI,"
                + " which runs this same code against the index.");
        return 0;
    }
}
