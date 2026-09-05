package com.botmaker.cli.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code releases/<YYYY-MM-DD-HHMM>.md} — what a release did, committed in the umbrella beside the
 * submodule pointers. {@code release.sh}'s {@code write_release_log} / {@code render_release_log}.
 *
 * <p><b>Everything in it was already computed and already printed; what was missing was a record.</b>
 * {@code verify_jitpack} had said {@code UNVERIFIED} on three consecutive releases, each read as a slow
 * JitPack queue, while {@code botmaker-studio-api} and {@code botmaker-session} had in fact stopped
 * <i>compiling</i> on JitPack — six of eight published modules unresolvable for two days, every release
 * "successful". A terminal scrollback is not a record; a committed file is.
 *
 * <p><b>It carries GitHub Actions too, and that column is the genuinely new one.</b> Every released module
 * publishes its own GitHub Release from its own {@code ci.yml} on the tag, and nothing here had ever looked
 * at whether that job passed: a tag can be pushed and JitPack perfectly green while the notes do not exist
 * because a workflow died on a missing secret.
 *
 * <p><b>It is written the moment the last tag is pushed</b>, with both verdicts {@code pending}, <i>before</i>
 * the five-minute poll. A log that only appears after the poll does not exist if the poll is interrupted —
 * and by then the tags are out, which is precisely the state worth recording.
 *
 * <p>The rendering always rewrites the whole file, so the two writers (a release, and {@code --status}) can
 * never leave a half-updated table behind.
 */
public final class ReleaseLog {

    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");
    private static final DateTimeFormatter HEADING = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** One module's line, plus whatever the two pollers have filled in so far. */
    public record Row(Module module, Version version, String jitpack, String actions,
                      String jitpackError, String actionsError) {

        public Row(Module module, Version version) {
            this(module, version, "", "", "", "");
        }

        public Row withJitpack(String verdict, String error) {
            return new Row(module, version, verdict, actions, error, actionsError);
        }

        public Row withActions(String verdict, String error) {
            return new Row(module, version, jitpack, verdict, jitpackError, error);
        }

        /**
         * The JitPack cell: {@code pending} until polled, and {@code n/a} for the two modules nobody
         * resolves — the pilot is an APK and Studio is an app packaged per-OS by its own CI.
         */
        String jitpackCell() {
            if (!jitpack.isBlank()) {
                return jitpack;
            }
            return onJitpack(module) ? "pending" : "n/a (not a Maven artifact)";
        }

        String actionsCell() {
            return actions.isBlank() ? "pending" : actions;
        }

        /** {@code botmaker-pilot} is the module the changelog gate exempts, and so the one with none. */
        String changelogCell() {
            return module == Module.PILOT ? "n/a (no CHANGELOG.md)" : "stamped";
        }
    }

    private ReleaseLog() {
    }

    /** Whether anybody resolves this module as a Maven artifact — {@code on_jitpack}. */
    public static boolean onJitpack(Module module) {
        return module != Module.PILOT && module != Module.STUDIO;
    }

    /** The file this run writes, named for the minute it was cut. */
    public static Path path(Path umbrella, LocalDateTime when) {
        return umbrella.resolve("releases").resolve(FILE.format(when) + ".md");
    }

    /** The whole file. Pure: the same rows always render the same bytes, which is what makes it diffable. */
    public static String render(LocalDateTime when, List<Row> rows) {
        StringBuilder out = new StringBuilder("# Release " + HEADING.format(when) + "\n\n");
        out.append("| module | version | tag | changelog | jitpack | actions |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            out.append("| ").append(row.module().directory())
                    .append(" | ").append(row.version())
                    .append(" | ").append(row.version().tag())
                    .append(" | ").append(row.changelogCell())
                    .append(" | ").append(row.jitpackCell())
                    .append(" | ").append(row.actionsCell())
                    .append(" |\n");
        }
        // The errors go in full UNDER the table rather than in it: a cell holds a verdict, and what a
        // reader needs six weeks later is the message Maven or Actions actually printed.
        if (rows.stream().anyMatch(row -> !row.jitpackError().isBlank() || !row.actionsError().isBlank())) {
            out.append("\n## Errors\n");
            for (Row row : rows) {
                append(out, row.module(), "jitpack", row.jitpackError());
                append(out, row.module(), "actions", row.actionsError());
            }
        }
        return out.toString();
    }

    private static void append(StringBuilder out, Module module, String column, String error) {
        if (!error.isBlank()) {
            out.append("\n**").append(module.directory()).append(" — ").append(column).append("**\n")
                    .append("```\n").append(error).append("\n```\n");
        }
    }

    /** The rows a run released, in the order they were tagged. */
    public static List<Row> rows(java.util.Map<Module, Version> released) {
        List<Row> rows = new ArrayList<>();
        for (Module module : Order.TAG) {
            Version version = released.get(module);
            if (version != null) {
                rows.add(new Row(module, version));
            }
        }
        return List.copyOf(rows);
    }

    /** Writes it, or says what it would have written. */
    public static Path write(Runner runner, Path umbrella, LocalDateTime when, List<Row> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        Path file = path(umbrella, when);
        if (runner.dryRun()) {
            runner.say("    (dry-run) would write " + file + " (" + rows.size() + " modules)");
            return null;
        }
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new ReleaseRefusal(file.getParent() + ": could not be created (" + e.getMessage() + ")");
        }
        runner.write(file, render(when, rows));
        runner.say("Release log: releases/" + file.getFileName());
        return file;
    }

    /**
     * Reads back the module/version pairs a log names, for {@code --status} to re-poll.
     *
     * <p>The table is the contract, and reading it back rather than keeping state beside it is what lets
     * {@code --status} run a week later, from a different machine, on a log somebody else's release wrote.
     */
    public static List<Row> read(Path log) {
        List<Row> rows = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(log);
        } catch (IOException e) {
            throw new ReleaseRefusal(log + ": could not be read (" + e.getMessage() + ")");
        }
        for (String line : lines) {
            if (!line.startsWith("| botmaker-")) {
                continue;
            }
            String[] cells = line.split("\\|");
            Module module = Module.byDirectory(cells[1].strip()).orElse(null);
            Version version = Version.parse(cells[2].strip()).orElse(null);
            if (module != null && version != null) {
                rows.add(new Row(module, version));
            }
        }
        return List.copyOf(rows);
    }

    /** The newest log in {@code releases/}, which is what {@code --status} with no argument re-polls. */
    public static Path newest(Path umbrella) {
        Path dir = umbrella.resolve("releases");
        try (var files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".md"))
                    .max(java.util.Comparator.comparing(file -> file.getFileName().toString()))
                    .orElseThrow(() -> new ReleaseRefusal(
                            "no release log in " + dir + " — nothing to re-poll."));
        } catch (IOException e) {
            throw new ReleaseRefusal("no release log in " + dir + " — nothing to re-poll.");
        }
    }
}
