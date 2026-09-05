package com.botmaker.cli;

import com.botmaker.cli.release.ChangelogGate;
import com.botmaker.cli.release.CiDepsGate;
import com.botmaker.cli.release.GatePlan;
import com.botmaker.cli.release.GateVerdict;
import com.botmaker.cli.release.JitpackPluginsGate;
import com.botmaker.cli.release.Module;
import com.botmaker.cli.release.Plan;
import com.botmaker.cli.release.ReleaseRefusal;
import com.botmaker.cli.release.ReleaseStatus;
import com.botmaker.cli.release.Runner;
import com.botmaker.cli.release.SdkGates;
import com.botmaker.cli.release.Version;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@code botmaker release} — the third noun, and the one whose decisions live in a library.
 *
 * <p><b>This class is a command line and nothing else.</b> Which modules a release cuts, what version each
 * gets, what forces what, the tag order and every gate belong to {@code com.botmaker.cli.release}, because
 * that package has three callers — this command, {@code .github/workflows/release.yml} and
 * {@code botmaker-dashboard} — and CI cannot run a JavaFX app, so the owner of those decisions cannot be
 * either of the other two. Same shape, same reason, as {@code com.botmaker.cli.validate}.
 *
 * <p><b>It cannot cut a release, and that is enforced here rather than remembered.</b> It builds a
 * {@link Runner#preview()} and has no flag that changes it. The port is verified by diffing this command's
 * output against {@code ./release.sh}'s for the same flags, and until those diffs are empty the script stays
 * the only thing that pushes a tag. A wrong tag is permanent and no exit code recalls one.
 *
 * <p>The ten module options are spelled out one per field rather than collected into a map: they are the
 * script's own flags, one for one, and {@code --help} listing them is half of what makes this command
 * usable.
 */
@Command(name = "release",
        header = "Preview what a cross-module release would do.",
        description = "The decide pass, the gates and the tag order, from com.botmaker.cli.release — the "
                + "port of release.sh. Previews only: it pushes nothing.",
        mixinStandardHelpOptions = true)
public final class ReleaseCommand implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    private static final String SPEC = "<version|level>";
    private static final String SPEC_HELP = "x.y.z, or patch|minor|major (default: ${FALLBACK-VALUE}).";

    @Option(names = "--all", arity = "0..1", fallbackValue = "patch", paramLabel = "<level>",
            description = "Every module, at this level (default: ${FALLBACK-VALUE}).")
    private String all;

    @Option(names = "--studio-api", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The plugin contract. " + SPEC_HELP)
    private String studioApi;

    @Option(names = "--plugin-toolkit", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The plugin widget toolkit. " + SPEC_HELP)
    private String pluginToolkit;

    @Option(names = "--plugin-host", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The plugin loader. " + SPEC_HELP)
    private String pluginHost;

    @Option(names = "--plugin-archetype", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "mvn archetype:generate. " + SPEC_HELP)
    private String pluginArchetype;

    @Option(names = "--cli", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The botmaker command and the validator. " + SPEC_HELP)
    private String cli;

    @Option(names = "--shared", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The host platform layer. " + SPEC_HELP)
    private String shared;

    @Option(names = "--session", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "Private display sessions. " + SPEC_HELP)
    private String session;

    @Option(names = "--sdk", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The bot runtime, and Studio's plugin #1. " + SPEC_HELP)
    private String sdk;

    @Option(names = "--studio", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The IDE. " + SPEC_HELP)
    private String studio;

    @Option(names = "--pilot", arity = "0..1", fallbackValue = "patch", paramLabel = SPEC,
            description = "The phone client. " + SPEC_HELP)
    private String pilot;

    @Option(names = "--force", description = "Release every requested module, changes or not.")
    private boolean force;

    /**
     * Off by default, and that is about the cutover rather than about taste.
     *
     * <p>The port is verified by diffing this command's output against {@code ./release.sh --dry-run}'s for
     * the same flags, and the diff has to be <b>empty</b>. Anything this prints that the script does not —
     * however useful — fails that test, so the one place the port improves on the script (a reason per
     * forcing edge, where the script keeps a shell comment) is opt-in until the script is gone.
     */
    @Option(names = "--why", description = "Also say why each forced module is in the release.")
    private boolean why;

    @Option(names = "--status", arity = "0..1", fallbackValue = "", paramLabel = "<file>",
            description = "Re-poll a releases/*.md instead of planning (default: the newest).")
    private String status;

    @Option(names = "--umbrella", paramLabel = "<dir>",
            description = "The umbrella checkout (default: the current directory).")
    private Path umbrella = Path.of("").toAbsolutePath();

    @Override
    public Integer call() {
        Runner runner = Runner.preview();
        try {
            if (!Files.isRegularFile(umbrella.resolve("release.sh"))) {
                parent.console().error("not a botmaker umbrella checkout: " + umbrella);
                return 2;
            }
            if (status != null) {
                ReleaseStatus.repoll(runner, umbrella,
                        status.isBlank() ? Optional.empty() : Optional.of(Path.of(status)));
                return 0;
            }
            return plan(runner);
        } catch (ReleaseRefusal refused) {
            parent.console().error(refused.getMessage());
            return 1;
        }
    }

    private int plan(Runner runner) {
        Map<Module, String> requested = requested();
        if (requested.isEmpty()) {
            parent.console().error("nothing to release — pass --all or a module flag.");
            return 2;
        }
        Plan plan = Plan.decide(umbrella, requested, force);

        runner.say("Release plan:");
        plan.planLines().forEach(runner::say);
        runner.say("Deciding what to release:");
        plan.decisionLines().forEach(runner::say);

        List<String> forcing = plan.forcingLines();
        if (why && !forcing.isEmpty()) {
            // release.sh keeps these as comments beside each decide call. A comment cannot be shown to the
            // operator asking why a module they never named is being released — but printing them by
            // default would break the cutover diff, so they are behind --why.
            runner.say("Forced into this release:");
            forcing.forEach(runner::say);
        }

        Map<Module, Version> releasing = plan.releasing();
        if (releasing.isEmpty()) {
            runner.say("Nothing to release.");
            return 0;
        }

        runner.say("Gates:");
        List<GateVerdict> refusals = gates(runner, releasing);

        runner.say("Tag order:");
        releasing.forEach((module, version) ->
                runner.say("    " + module.directory() + " " + version.tag()));

        if (!refusals.isEmpty()) {
            refusals.forEach(refused -> parent.console().error(refused.refusal()));
            return 1;
        }
        runner.say("(preview only — release.sh still cuts the tags)");
        return 0;
    }

    /**
     * Runs every gate the plan calls for, printing each verdict and collecting the refusals.
     *
     * <p>All of them run even after one refuses: the operator is about to fix something and wants the whole
     * list, not the first item of it. The script stops at the first {@code die}, which is the one place this
     * deliberately reports more — a refusal is still a refusal, and the exit code is unchanged.
     */
    private List<GateVerdict> gates(Runner runner, Map<Module, Version> releasing) {
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
        return refusals;
    }

    private void record(Runner runner, List<GateVerdict> refusals, GateVerdict verdict) {
        if (verdict.stops()) {
            refusals.add(verdict);
        } else if (!verdict.line().isBlank()) {
            runner.say(verdict.line());
        }
    }

    /** The flags, as module to spec. An explicit module beats {@code --all} — {@code release.sh}'s rule. */
    private Map<Module, String> requested() {
        Map<Module, String> out = new EnumMap<>(Module.class);
        put(out, Module.STUDIO_API, studioApi);
        put(out, Module.PLUGIN_TOOLKIT, pluginToolkit);
        put(out, Module.PLUGIN_HOST, pluginHost);
        put(out, Module.PLUGIN_ARCHETYPE, pluginArchetype);
        put(out, Module.CLI, cli);
        put(out, Module.SHARED, shared);
        put(out, Module.SESSION, session);
        put(out, Module.SDK, sdk);
        put(out, Module.STUDIO, studio);
        put(out, Module.PILOT, pilot);
        if (all != null) {
            for (Module module : Module.values()) {
                out.putIfAbsent(module, all);
            }
        }
        return out;
    }

    private static void put(Map<Module, String> out, Module module, String spec) {
        if (spec != null) {
            out.put(module, spec);
        }
    }
}
