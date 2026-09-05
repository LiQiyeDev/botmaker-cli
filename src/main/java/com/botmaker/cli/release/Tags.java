package com.botmaker.cli.release;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A module's released versions, read from its git tags — {@code release.sh}'s {@code latest_version}.
 *
 * <p>The script's pipeline is
 * {@code git tag --list | sed -E 's/^v//' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -1},
 * and {@link #highest} is exactly that, split out as a pure function so the ordering can be tested without
 * a checkout. The one place a port of it goes wrong silently is {@code sort -V}: it orders {@code 1.9.0}
 * before {@code 1.10.0}, and a text comparison does not. A wrong answer here is not a wrong printout — it
 * is a bump computed off the wrong base, cut as a tag, which cannot be edited.
 *
 * <p><b>The fetch is not optional and its failure is not fatal</b>, both as the script has it. Without it,
 * a bump is computed off whatever tags this checkout happens to hold, so a release cut from another machine
 * is invisible and the next {@code --all} recuts a version that already exists. And a fetch that fails
 * (offline, no credential, no {@code origin} yet) leaves the local tags standing, which is the right
 * degradation for a dry run and is caught for real by the {@code origin} preflight.
 */
public final class Tags {

    private Tags() {
    }

    /**
     * The module's newest released version, or empty when it has never been tagged.
     *
     * <p>Empty is an ordinary state — it is what a module gets before its first release — and callers turn
     * it into {@link Version#ZERO} for the bump, which is the script's {@code [[ -z "$cur" ]] && cur="0.0.0"}.
     */
    public static Optional<Version> latest(Path umbrella, Module module) {
        Path dir = umbrella.resolve(module.directory());
        // Best-effort, exactly as the script's `|| true`: a failed fetch must not stop a dry run.
        Git.run(dir, "fetch", "--tags", "--quiet", "origin");
        return highest(Git.run(dir, "tag", "--list").lines());
    }

    /**
     * The highest {@code x.y.z} among tag names, ignoring every tag that is not one.
     *
     * <p>Ignoring rather than refusing: a repository may carry tags this project did not cut, and a release
     * that stopped because somebody tagged {@code demo-2026} would be refusing over something that cannot
     * affect what is published.
     */
    public static Optional<Version> highest(List<String> tagNames) {
        return tagNames.stream()
                .map(Version::parse)
                .flatMap(Optional::stream)
                .max(Version::compareTo);
    }
}
