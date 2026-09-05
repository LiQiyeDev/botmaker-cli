package com.botmaker.cli.release;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What GitHub Actions made of a tag — {@code release.sh}'s {@code poll_actions}.
 *
 * <p><b>Each module publishes its own GitHub Release from its own {@code ci.yml}, on the tag</b>, and until
 * 2026-09-04 nothing here ever looked at whether that job passed. A tag can be pushed and JitPack perfectly
 * green while the release notes simply do not exist, because the workflow died on a missing secret.
 *
 * <p><b>{@code --branch <tag>} is not a mistake.</b> A tag-triggered run records the tag in
 * {@code headBranch}, and it is the only filter {@code gh run list} offers that isolates one release's runs.
 * Several workflows can fire on one tag — Studio's package matrix, its pages deploy and its JReleaser step
 * are three — so <b>the verdict is the worst of them</b> and the failures are named.
 */
public final class Actions {

    /**
     * @param verdict the cell for the log
     * @param error   the failing runs and their URLs, empty when nothing failed
     */
    public record Poll(String verdict, String error) {
    }

    private Actions() {
    }

    public static Poll poll(Module module, Version version) {
        if (!Proc.onPath("gh")) {
            return new Poll("unknown (no gh on PATH)", "");
        }
        Proc.Result run = Proc.run(Path.of("."), "gh", "run", "list",
                "--repo", CleanRoom.OWNER + "/" + module.directory(),
                "--branch", version.tag(), "--limit", "20",
                "--json", "name,status,conclusion,url",
                "--jq", ".[] | [.name, .status, .conclusion, .url] | @tsv");
        return verdict(run.ok() ? run.out() : "", version);
    }

    /** The rule over {@code gh}'s tab-separated output — pure, so every verdict is testable offline. */
    static Poll verdict(String tsv, Version version) {
        List<String> failed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int running = 0;
        int total = 0;
        for (String line : tsv.lines().filter(l -> !l.isBlank()).toList()) {
            String[] cells = line.split("\t", -1);
            String name = cells[0].strip();
            if (name.isEmpty()) {
                continue;
            }
            total++;
            String status = cells.length > 1 ? cells[1].strip() : "";
            if (!"completed".equals(status)) {
                running++;
                continue;
            }
            String conclusion = cells.length > 2 ? cells[2].strip() : "";
            String url = cells.length > 3 ? cells[3].strip() : "";
            // `skipped` counts as fine: a job that correctly did not apply to this tag is not a failure.
            if (!"success".equals(conclusion) && !"skipped".equals(conclusion)) {
                failed.add(name);
                errors.add(name + ": " + conclusion + " — " + url);
            }
        }
        if (total == 0) {
            // NOT the same as a pull request with no check run yet: a tag is finished, so nothing more will
            // fire and this is a finding rather than a state on the way to one.
            return new Poll("no run on " + version.tag(), "");
        }
        if (!failed.isEmpty()) {
            return new Poll("FAILED — " + String.join(", ", failed), String.join("\n", errors));
        }
        if (running > 0) {
            return new Poll("running (" + running + " of " + total + ")", "");
        }
        return new Poll("success (" + total + ")", "");
    }
}
