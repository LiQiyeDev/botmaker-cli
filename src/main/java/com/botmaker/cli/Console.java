package com.botmaker.cli;

import com.botmaker.cli.validate.CheckResult;
import com.botmaker.cli.validate.Status;

import java.util.List;

/**
 * Everything this program prints.
 *
 * <p>Diagnostics go to {@code stderr} and results go to {@code stdout}, which is not decoration: a plugin
 * author pipes {@code botmaker publish --dry-run} into a file, and a progress line landing in that file
 * makes it not-JSON.
 */
public final class Console {

    /** The escape, written as an octal escape rather than a literal byte so the source stays greppable. */
    private static final String ESC = "\033";

    private final boolean quiet;
    private final boolean colour;

    Console(boolean quiet) {
        this.quiet = quiet;
        // No colour when stdout is redirected. System.console() is null under a pipe and under CI, which is
        // exactly where an escape sequence turns a readable log into a smear of brackets.
        this.colour = System.console() != null && !"dumb".equals(System.getenv("TERM"));
    }

    /** Progress — suppressed by {@code --quiet}, and never on stdout. */
    public void step(String message) {
        if (!quiet) {
            System.err.println(message);
        }
    }

    /** The thing the user asked for. */
    public void out(String message) {
        System.out.println(message);
    }

    public void error(String message) {
        System.err.println(paint("31", "error: ") + message);
    }

    public void warn(String message) {
        System.err.println(paint("33", "warning: ") + message);
    }

    /**
     * The validation report: one line per check, then every detail indented under it.
     *
     * <p>All seven are always printed, passes included. A report that listed only failures would leave an
     * author unable to tell a check that passed from one that never ran, and {@link Status#SKIP} is a real
     * outcome here — the editors check skips without JavaFX, and the registry-collision half of two checks
     * cannot be asked at all outside the registry's own CI.
     */
    public void report(List<CheckResult> results) {
        for (CheckResult result : results) {
            out(badge(result.status()) + " " + pad(result.check().id()) + result.check().title());
            for (String detail : result.detail()) {
                out("         " + detail);
            }
        }
    }

    private String badge(Status status) {
        return switch (status) {
            case PASS -> paint("32", "  ok  ");
            case FAIL -> paint("31", " FAIL ");
            case SKIP -> paint("33", " skip ");
        };
    }

    private static String pad(String id) {
        return id.length() >= 14 ? id + " " : id + " ".repeat(14 - id.length());
    }

    private String paint(String code, String text) {
        return colour ? ESC + "[" + code + "m" + text + ESC + "[0m" : text;
    }
}
