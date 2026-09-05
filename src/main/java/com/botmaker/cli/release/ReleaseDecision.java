package com.botmaker.cli.release;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Whether a requested module actually gets a tag — {@code release.sh}'s {@code should_release}, with its
 * {@code SKIP_REASON} carried in the answer instead of a global.
 *
 * <p>Four ways to release and two ways to skip, in the script's own order:
 *
 * <ol>
 *   <li>{@code --force} — release every requested module, changes or not.</li>
 *   <li><b>forced</b> — an upstream module in <i>this run</i> edits this one's pom or pins, so its published
 *       artifact really would differ. That flag is computed by the caller from the versions decided so far,
 *       which is why the decisions are taken in dependency order and the tag order is then free.</li>
 *   <li>An <b>exact</b> version — the operator said the number, so they have already answered the
 *       question.</li>
 *   <li>A bump level, and {@link ChangeKind#REAL}.</li>
 * </ol>
 *
 * <p><b>The two skips are separate sentences because they have separate causes</b>, and a maintainer reading
 * the plan needs to tell them apart: a module with no commits at all is finished, and a module whose commits
 * are all markdown is one {@code --force} away from a tag that publishes a byte-identical artifact.
 *
 * @param releasing  whether a tag is cut
 * @param skipReason the script's own sentence when it is not, empty when it is
 */
public record ReleaseDecision(boolean releasing, String skipReason) {

    private static final ReleaseDecision RELEASING = new ReleaseDecision(true, "");

    /**
     * The rule, over facts already read. Pure, so every arm is testable without a checkout.
     *
     * @param latest only used to spell the {@code docs} refusal, which names the tag being compared against
     */
    public static ReleaseDecision decide(boolean force, boolean forced, VersionSpec spec,
                                         ChangeKind kind, Optional<Version> latest) {
        if (force || forced || spec instanceof VersionSpec.Exact) {
            return RELEASING;
        }
        return switch (kind) {
            case REAL -> RELEASING;
            case DOCS -> new ReleaseDecision(false, "only docs since v"
                    + latest.map(Version::toString).orElse("")
                    + " — skipping (the artifact would be identical; --force overrides)");
            case NONE -> new ReleaseDecision(false, "no changes since its latest tag — skipping");
        };
    }

    /**
     * The same rule, reading the checkout only when the answer depends on it.
     *
     * <p>The short-circuit is not an optimisation of the port's own making — it is what the script does, and
     * it matters: {@code change_kind} shells to git twice per module, and under {@code --force} the answer
     * cannot change anything.
     */
    public static ReleaseDecision read(Path umbrella, Module module, boolean force, boolean forced,
                                       VersionSpec spec, Optional<Version> latest) {
        if (force || forced || spec instanceof VersionSpec.Exact) {
            return RELEASING;
        }
        return decide(false, false, spec, ChangeKind.read(umbrella, module, latest), latest);
    }

    /** The line the decide pass prints for this module, given the version it would cut. */
    public String line(Module module, Version version) {
        return "    " + module.directory() + ": "
                + (releasing ? "releasing v" + version : skipReason);
    }
}
