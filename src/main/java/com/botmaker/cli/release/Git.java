package com.botmaker.cli.release;

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

    /**
     * Runs {@code git -C <dir> <args…>} and captures everything it wrote.
     *
     * <p>{@code -C} rather than a working directory, exactly as the script spells it: a submodule's
     * {@code .git} here is a {@code gitdir:} <i>file</i>, and the two are not equivalent for every command
     * once worktrees are in play. Launching is {@link Proc}'s, so there is one place a missing tool becomes
     * an exit code.
     */
    public static Result run(Path dir, String... args) {
        String[] argv = new String[args.length + 3];
        argv[0] = "git";
        argv[1] = "-C";
        argv[2] = dir.toString();
        System.arraycopy(args, 0, argv, 3, args.length);
        Proc.Result result = Proc.run(Path.of("."), argv);
        return new Result(result.exit(), result.out());
    }

    /** Stdout when the command succeeded, empty otherwise — a question rather than an action. */
    public static Optional<String> capture(Path dir, String... args) {
        Result result = run(dir, args);
        return result.ok() ? Optional.of(result.out().strip()) : Optional.empty();
    }
}
