package com.botmaker.cli.release;

import java.util.Optional;

/**
 * A bump level: {@code patch}, {@code minor} or {@code major}.
 *
 * <p>Spelled lower-case on the command line and matched exactly, as the script matches it — {@code Patch}
 * and {@code PATCH} are refused there and must be refused here, or the two implementations disagree about
 * which inputs are legal before they disagree about anything interesting.
 *
 * <p><b>{@code patch} is also what a bare module flag means.</b> {@code release.sh}'s {@code take_optional}
 * gives {@code OPT_VAL="patch"} when the next token is another flag or absent, so {@code --cli} and
 * {@code --cli patch} are the same request.
 */
public enum Level {

    PATCH, MINOR, MAJOR;

    /** The default a bare module flag carries. */
    public static final Level DEFAULT = PATCH;

    /** The word as the command line spells it. */
    public String spelling() {
        return name().toLowerCase();
    }

    /** The level a word names, or empty — the caller turns that into the script's own refusal. */
    public static Optional<Level> of(String word) {
        for (Level level : values()) {
            if (level.spelling().equals(word)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}
