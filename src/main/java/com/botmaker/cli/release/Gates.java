package com.botmaker.cli.release;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every gate a release runs, in the order {@link GatePlan} places them.
 *
 * <p><b>All of them run even after one refuses</b>, and the exit code is unchanged either way. The script
 * stops at its first {@code die}; this reports the whole list, because the operator is about to go and fix
 * something and a second refusal discovered on the next run is a second round trip. A refusal is still a
 * refusal — nothing is tagged.
 */
public final class Gates {

    private Gates() {
    }

    /**
     * @param releasing the versions decided, because the changelog gate is asked about a specific one
     * @return the refusals, empty when the release may proceed
     */
    public static List<GateVerdict> run(Runner runner, Path umbrella,
                                        Map<Module, Version> releasing, boolean force) {
        Set<Module> modules = releasing.keySet();
        List<GateVerdict> refusals = new ArrayList<>();

        for (Module module : GatePlan.changelog(modules)) {
            record(runner, refusals, ChangelogGate.check(umbrella, module, releasing.get(module), force));
        }
        for (Module module : GatePlan.ciDeps(modules)) {
            record(runner, refusals, CiDepsGate.check(umbrella, module, force));
        }
        for (Module module : GatePlan.jitpackPlugins(modules)) {
            record(runner, refusals, JitpackPluginsGate.check(umbrella, module, force));
        }
        if (GatePlan.sdkGates(modules)) {
            record(runner, refusals, SdkGates.apiPointers(umbrella, releasing.get(Module.SDK), force));
            record(runner, refusals, SdkGates.sdkPlugin(umbrella, force));
        }
        return List.copyOf(refusals);
    }

    private static void record(Runner runner, List<GateVerdict> refusals, GateVerdict verdict) {
        if (verdict.stops()) {
            refusals.add(verdict);
        } else if (!verdict.line().isBlank()) {
            runner.say(verdict.line());
        }
    }
}
