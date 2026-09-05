package com.botmaker.cli.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Every side effect a release has, behind one switch — {@code release.sh}'s {@code run} and
 * {@code run_sh}.
 *
 * <p><b>A dry run does everything except the last step.</b> It decides, gates, computes and prints exactly
 * what a real run would, and echoes each command as {@code     $ …} instead of executing it. That is what
 * makes {@code --dry-run} worth trusting: the plan on screen is produced by the same code path, not by a
 * second "preview" implementation that can drift from what the release actually does.
 *
 * <p><b>So nothing in this package may write, commit, tag or push except through here.</b> A direct
 * {@code Files.writeString} or {@code Git.run} on a write path is a line that ignores {@code --dry-run},
 * and it will be discovered by a tag that exists, which cannot be edited.
 *
 * @param dryRun when true, commands are echoed and not run
 * @param out    where the echo goes — the caller's, because a library has no opinion about stdout
 */
public record Runner(boolean dryRun, Consumer<String> out) {

    /** A runner that prints to stdout and executes. */
    public static Runner real() {
        return new Runner(false, System.out::println);
    }

    /** A runner that prints to stdout and executes nothing. */
    public static Runner preview() {
        return new Runner(true, System.out::println);
    }

    /** Runs {@code git -C <dir> <args…>}, echoed as the script echoes it. */
    public Proc.Result git(Path dir, String... args) {
        String[] argv = new String[args.length + 3];
        argv[0] = "git";
        argv[1] = "-C";
        argv[2] = dir.toString();
        System.arraycopy(args, 0, argv, 3, args.length);
        return run(argv);
    }

    /** Runs a command, echoed. A dry run returns a successful empty result — nothing ran, nothing failed. */
    public Proc.Result run(String... argv) {
        out.accept("    $ " + String.join(" ", argv));
        return dryRun ? new Proc.Result(0, "") : Proc.run(Path.of("."), argv);
    }

    /**
     * Writes a whole file, echoed as the heredoc the script uses.
     *
     * <p>The echo names the file rather than reproducing its content, which is what {@code run_sh}'s own
     * output does for a heredoc: the interesting part of a {@code .deps.env} is the pins, and those are
     * printed by the line above it.
     */
    public void write(Path file, String content) {
        out.accept("    $ cat > " + file + " <<'DEPS_EOF' … DEPS_EOF");
        if (dryRun) {
            return;
        }
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new ReleaseRefusal(file + ": could not be written (" + e.getMessage() + ")");
        }
    }

    /** One line of narration, the script's {@code info} without its colour. */
    public void say(String line) {
        out.accept(line);
    }

    /** Convenience for the many places that build an argument list. */
    public Proc.Result run(List<String> argv) {
        return run(argv.toArray(String[]::new));
    }
}
