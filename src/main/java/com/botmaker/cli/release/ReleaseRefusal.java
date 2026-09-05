package com.botmaker.cli.release;

/**
 * What {@code release.sh}'s {@code die} is, in Java.
 *
 * <p>The script's failure mode is one line on stderr prefixed {@code error: } and a non-zero exit, and the
 * port has to reproduce it <b>character for character</b> — the cutover discipline for this package is that
 * both implementations' output is diffed over a matrix of flag combinations, so a refusal reworded into
 * idiomatic Java is a failing diff even when it refuses the same thing for the same reason.
 *
 * <p>That is also why this exists at all rather than {@link IllegalArgumentException}: the message is not a
 * programmer's diagnostic, it is the product. A caller catches this, prints {@code "error: " + getMessage()}
 * and exits 1, and nothing else in the package writes to a stream — this library prints nothing, spawns no
 * UI and knows no command line, exactly as {@code com.botmaker.cli.validate} does not.
 */
public class ReleaseRefusal extends RuntimeException {

    public ReleaseRefusal(String message) {
        super(message);
    }

    /** The line the script writes: {@code error: <message>}. */
    public String errorLine() {
        return "error: " + getMessage();
    }
}
