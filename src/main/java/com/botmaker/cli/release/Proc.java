package com.botmaker.cli.release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * One external command, run from a directory, with everything it wrote captured.
 *
 * <p><b>Most of {@code release.sh} is this and stays this.</b> Only five things in it are algorithms; the
 * rest shells to {@code git}, {@code gh}, {@code mvn}, {@code python3} and each module's own
 * {@code tools/changelog-section.sh}, which Java does at two to three times the line count and no gain — and
 * in the extractor's case must not do at all, since a second implementation of it is exactly what that file
 * exists to prevent.
 *
 * <p><b>Every failure to launch is a {@link Result}, never an exception.</b> A missing tool is an answer a
 * caller acts on — the gates turn it into {@code SKIPPED} — and the script reaches the same state through a
 * non-zero exit with a message on stderr.
 */
public final class Proc {

    /** @param out stdout and stderr together, in the order they were written */
    public record Result(int exit, String out) {

        public boolean ok() {
            return exit == 0;
        }

        public List<String> lines() {
            return out.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        }
    }

    private Proc() {
    }

    public static Result run(Path dir, String... argv) {
        try {
            Process process = new ProcessBuilder(argv)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String out;
            try (var stdout = process.getInputStream()) {
                out = new String(stdout.readAllBytes());
            }
            return new Result(process.waitFor(), out);
        } catch (IOException e) {
            // No such command, or no such directory.
            return new Result(127, e.getMessage() == null ? "" : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(130, "interrupted");
        }
    }

    /** Whether a command is on {@code PATH} — the script's {@code command -v <tool>}. */
    public static boolean onPath(String command) {
        return run(Path.of("."), "sh", "-c", "command -v " + command + " >/dev/null 2>&1").ok();
    }
}
