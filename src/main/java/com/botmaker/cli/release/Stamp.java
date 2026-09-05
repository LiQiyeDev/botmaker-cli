package com.botmaker.cli.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Renames {@code ## [Unreleased]} to {@code ## [<version>] — <date>} in the module's release commit —
 * {@code release.sh}'s {@code stamp_changelog}.
 *
 * <p><b>This is the half that makes {@code --all} work.</b> A changelog is written as the work happens, when
 * the version number is not knowable — it is what the decide pass <i>computes</i>, by bumping each module
 * off its own latest tag. So the section is called {@code [Unreleased]}, {@link ChangelogGate} accepts that,
 * and this stamps the number onto it a moment before the tag. Until 2026-09-02 the maintainer did it by
 * hand, guessing the number the script was about to choose.
 *
 * <p><b>Idempotent by checking for the stamped heading first</b>, which is the case a resumed release hits:
 * a run that died after tagging one module and is re-run must not stamp a second heading onto a file that
 * already has one. Doing nothing when neither heading is present is equally deliberate — the gate already
 * refused, or {@code --force} was passed and the maintainer has said to release without notes.
 */
public final class Stamp {

    private Stamp() {
    }

    /** Stamps the file if there is something to stamp; answers what it did, for the caller to print. */
    public static Optional<String> changelog(Runner runner, Path umbrella, Module module, Version version) {
        Path file = umbrella.resolve(module.directory()).resolve("CHANGELOG.md");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();                       // the pilot has none, and is exempt
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new ReleaseRefusal(file + ": could not be read (" + e.getMessage() + ")");
        }
        String stamped = "## [" + version + "] — " + LocalDate.now();
        if (lines.stream().anyMatch(line -> line.startsWith("## [" + version + "]"))) {
            return Optional.empty();                       // already stamped — a resumed release
        }
        int heading = indexOfUnreleased(lines);
        if (heading < 0) {
            return Optional.empty();                       // nothing to stamp
        }
        runner.say("  stamping " + module.directory() + " CHANGELOG.md: [Unreleased] -> [" + version + "]");
        // The FIRST such heading only, as the script's `sed '0,/…/s//…/'` does: a changelog carrying both a
        // stamped section and a fresh [Unreleased] is the ordinary state one release after another.
        lines.set(heading, stamped);
        runner.write(file, String.join("\n", lines) + "\n");
        return Optional.of(stamped);
    }

    private static int indexOfUnreleased(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("## [Unreleased]")) {
                return i;
            }
        }
        return -1;
    }
}
