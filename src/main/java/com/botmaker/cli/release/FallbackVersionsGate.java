package com.botmaker.cli.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuse a Studio release whose fallback constants name a version nobody published —
 * {@code release.sh}'s {@code check_fallback_versions}.
 *
 * <p><b>Two string literals in Studio's source are pins Studio <i>writes</i> rather than pins Studio
 * <i>has</i></b>: {@code MavenService.SDK_FALLBACK_VERSION} and {@code MavenService.TOOLKIT_FALLBACK_VERSION},
 * which are what a freshly generated bot's pom declares. Neither is a dependency, so nothing in a build
 * resolves them and no compiler, test or JitPack poll can see one go wrong. They are moved by a {@code sed}
 * over the literal on every {@code --sdk} / {@code --plugin-toolkit} release, and the {@code sed} runs only
 * if Studio is in that run — which is the hole {@link ForcingGate} closes from the other side.
 *
 * <p><b>This is the failure mode of three consecutive Studio releases.</b> 1.0.34, 1.0.35 and 1.0.36 each
 * exist only because the previous one pointed the constant at an SDK tag whose JitPack build had published
 * nothing, so a bot created by that Studio could not resolve its own SDK on a first build. 1.0.37 then
 * shipped the opposite error — a tag that resolves, but the wrong one, {@code 1.1.5} where its own
 * changelog said {@code 1.1.6}.
 *
 * <p><b>What it checks is existence, not correctness</b>, and the line is drawn there deliberately.
 * <i>Which</i> SDK a new project should pin is a judgement — an older one may be the right default while a
 * newer one settles. That a tag exists is not a judgement, and it is the half that has actually gone wrong
 * four times.
 *
 * <p><b>A module being cut in this same run is exempt</b>, because the release has not {@code sed}ded yet:
 * at gate time the constant still holds the previous value, and refusing it would refuse every release that
 * moves it. The pair is the point — this gate answers for the constants nobody is about to touch, and
 * {@link ForcingGate} guarantees that a run touching one includes the Studio release that touches it.
 */
public final class FallbackVersionsGate {

    /** The constant, and the module whose tags it must name. */
    private static final Map<Module, String> CONSTANTS = new EnumMap<>(Map.of(
            Module.SDK, "SDK_FALLBACK_VERSION",
            Module.PLUGIN_TOOLKIT, "TOOLKIT_FALLBACK_VERSION"));

    /** Studio's file holding both, relative to that module's directory. */
    static final String SOURCE =
            "src/main/java/com/botmaker/studio/services/MavenService.java";

    private FallbackVersionsGate() {
    }

    public static GateVerdict check(Path umbrella, Map<Module, Version> releasing, boolean force) {
        Path source = umbrella.resolve(Module.STUDIO.directory()).resolve(SOURCE);
        String text;
        try {
            text = Files.readString(source);
        } catch (IOException e) {
            // Unreadable is SKIPPED, not REFUSED: a gate must not stop a release over what it cannot read.
            return GateVerdict.skipped("  studio: " + SOURCE + " unreadable — fallback gate skipped");
        }

        List<String> problems = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        for (Map.Entry<Module, String> entry : CONSTANTS.entrySet()) {
            Module module = entry.getKey();
            String constant = entry.getValue();
            if (releasing.containsKey(module)) {
                // This release moves it; the sed has not run yet, so the current value proves nothing.
                continue;
            }
            Optional<String> literal = read(text, constant);
            if (literal.isEmpty()) {
                // Absent is not a failure either — Phase 2 removes one of these outright.
                continue;
            }
            Optional<Version> version = Version.parse(literal.get());
            if (version.isEmpty()) {
                problems.add("     " + constant + " = \"" + literal.get()
                        + "\" is not an x.y.z version.");
                continue;
            }
            if (Tags.existingRef(umbrella, module, version.get()).isEmpty()) {
                problems.add("     " + constant + " = \"" + literal.get() + "\" names no tag of "
                        + module.directory() + ", so every project this Studio creates pins a version\n"
                        + "     that cannot be resolved. Bump it, or release " + module.flag()
                        + " in this run so the sed moves it.");
                continue;
            }
            checked.add(constant);
        }

        if (problems.isEmpty()) {
            return GateVerdict.ok(checked.isEmpty()
                    ? "  studio: no fallback constant to check this run — ok"
                    : "  studio: " + String.join(", ", checked) + " resolve to published tags — ok");
        }
        if (force) {
            return GateVerdict.forced("  studio: a fallback constant names no published tag — FORCED");
        }
        return GateVerdict.refused("studio: a fallback version constant names nothing published.\n"
                + String.join("\n", problems) + "\n"
                + "     These are pins Studio WRITES into a generated bot's pom, so nothing in a build\n"
                + "     resolves them and no test can see one go wrong.  --force overrides.");
    }

    /** The {@code "x.y.z"} a {@code public static final String <name> = "…";} declares. */
    static Optional<String> read(String source, String constant) {
        Matcher matcher = Pattern.compile(
                "String\\s+" + Pattern.quote(constant) + "\\s*=\\s*\"([^\"]*)\"").matcher(source);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
