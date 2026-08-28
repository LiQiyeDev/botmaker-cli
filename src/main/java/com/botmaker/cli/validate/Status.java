package com.botmaker.cli.validate;

/** How a {@link Check} came out. */
public enum Status {

    PASS,

    FAIL,

    /**
     * The check could not be asked here — the classpath had already failed, or the environment cannot
     * answer it. Deliberately distinct from {@link #PASS}: a report that folded skips into passes would
     * claim coverage it does not have, and the one check that skips routinely ({@link Check#EDITORS},
     * without JavaFX) is the one whose failure is hardest to find later.
     */
    SKIP
}
