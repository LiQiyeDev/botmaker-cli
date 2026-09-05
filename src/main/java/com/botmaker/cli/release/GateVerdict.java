package com.botmaker.cli.release;

/**
 * What a gate answered — {@code release.sh}'s four outcomes, which are four and not two.
 *
 * <ul>
 *   <li>{@link Status#OK} — checked and passed; the script prints one line.</li>
 *   <li>{@link Status#SKIPPED} — <b>could not be checked</b>, and that is not a failure: no {@code mvn} on
 *       {@code PATH}, no {@code python3}, a module with no {@code ci.yml}. A gate must not stop a release
 *       over what it cannot read, or every machine missing a tool becomes a machine that cannot release.</li>
 *   <li>{@link Status#FORCED} — checked, failed, and {@code --force} was passed. Still printed, still
 *       distinguishable from a pass, because a maintainer reading the log later needs to know a gate was
 *       overruled rather than satisfied.</li>
 *   <li>{@link Status#REFUSED} — the release stops.</li>
 * </ul>
 *
 * <p><b>Where a gate runs is as load-bearing as what it refuses.</b> Every one of these belongs to the
 * decide pass, before the first tag is pushed: by the time tagging starts, pilot and studio are already
 * tagged and their CI is already running, and a pushed tag cannot be edited. See {@link GatePlan}.
 *
 * @param line    the one line the run prints, without the script's {@code ==>} prefix
 * @param refusal the whole refusal, {@code die}'s message, empty unless {@link Status#REFUSED}
 */
public record GateVerdict(Status status, String line, String refusal) {

    public enum Status { OK, SKIPPED, FORCED, REFUSED }

    public static GateVerdict ok(String line) {
        return new GateVerdict(Status.OK, line, "");
    }

    public static GateVerdict skipped(String line) {
        return new GateVerdict(Status.SKIPPED, line, "");
    }

    public static GateVerdict forced(String line) {
        return new GateVerdict(Status.FORCED, line, "");
    }

    public static GateVerdict refused(String refusal) {
        return new GateVerdict(Status.REFUSED, "", refusal);
    }

    /** Whether the release stops here. */
    public boolean stops() {
        return status == Status.REFUSED;
    }

    /** Turns a refusal into the exception the run exits on; anything else is returned unchanged. */
    public GateVerdict orThrow() {
        if (stops()) {
            throw new ReleaseRefusal(refusal);
        }
        return this;
    }
}
