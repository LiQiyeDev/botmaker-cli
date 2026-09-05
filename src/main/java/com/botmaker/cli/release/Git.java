package com.botmaker.cli.release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Running {@code git} against one submodule.
 *
 * <p><b>A process, on purpose, and this is the standing rule for the whole port.</b> Only five things in
 * {@code release.sh} are algorithms — the decide pass, the bump arithmetic, {@code dep_tag}, the forcing
 * rules and the tag order. Everything else is shelling to {@code git}, {@code gh}, {@code mvn} and
 * {@code curl}, which Java does at two to three times the line count and no gain. A JGit here would also be
 * a second opinion about the working copy's remotes, identity and hooks, which are the maintainer's
 * configuration and not this program's.
 *
 * <p>It prints nothing. The package is a library with callers in a terminal, a workflow and a desktop app,
 * so what a command <i>looks like</i> while it runs belongs to whoever called it.
 */
public final class Git {

    /** What one {@code git} invocation produced. Stderr is folded in, as the script's own capture does. */
    public record Result(int exit, String out) {

        public boolean ok() {
            return exit == 0;
        }

        public List<String> lines() {
            return out.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        }
    }

    private Git() {
    }

    /** Runs {@code git -C <dir> <args…>} and captures everything it wrote. */
    public static Result run(Path dir, String... args) {
        String[] argv = new String[args.length + 3];
        argv[0] = "git";
        argv[1] = "-C";
        argv[2] = dir.toString();
        System.arraycopy(args, 0, argv, 3, args.length);
        try {
            Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            String out;
            try (var stdout = process.getInputStream()) {
                out = new String(stdout.readAllBytes());
            }
            return new Result(process.waitFor(), out);
        } catch (IOException e) {
            // No git on PATH, or no such directory. Both are answers a caller acts on rather than crashes
            // over — the script reaches the same state as a non-zero exit with a message on stderr.
            return new Result(127, e.getMessage() == null ? "" : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(130, "interrupted");
        }
    }

    /** Stdout when the command succeeded, empty otherwise — a question rather than an action. */
    public static Optional<String> capture(Path dir, String... args) {
        Result result = run(dir, args);
        return result.ok() ? Optional.of(result.out().strip()) : Optional.empty();
    }
}
