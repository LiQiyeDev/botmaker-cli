package com.botmaker.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code botmaker new <name>} — the archetype, with the prompts already answered.
 *
 * <p><b>It shells to {@code botmaker-plugin-archetype} and carries no templates of its own.</b> The
 * generated shape is three dependency scopes, a services file, a palette and an editor, and every one of
 * them is wrong in a way that produces no compile error — so it must have exactly one source of truth. Two
 * copies of a skeleton drift, and they drift silently, because nothing ever compiles both.
 *
 * <p>What this adds over typing {@code mvn archetype:generate} is the part nobody remembers: the archetype's
 * own coordinate, {@code -DinteractiveMode=false}, and sane values for the four required properties derived
 * from one name.
 */
final class NewCommand {

    private static final String ARCHETYPE_GROUP = "com.github.LiQiyeDev";
    private static final String ARCHETYPE_ARTIFACT = "botmaker-plugin-archetype";

    /**
     * The BotMaker versions written into the generated pom.
     *
     * <p>{@code main-SNAPSHOT} is the archetype's own default and this passes it through rather than
     * substituting a release. A released tag baked in here would owe an edit on every contract release and
     * age silently between them; {@code main-SNAPSHOT} is a real JitPack coordinate that never goes stale,
     * and it is wrong in the direction the generated README tells the author to correct.
     */
    private static final String DEFAULT_BOTMAKER_VERSION = "main-SNAPSHOT";

    private final Console console;
    private final Mvn mvn;

    NewCommand(Console console, Mvn mvn) {
        this.console = console;
        this.mvn = mvn;
    }

    int run(Args args) throws IOException {
        String name = args.at(1);
        if (name == null || name.isBlank()) {
            console.error("usage: botmaker new <artifact-id> [--group <groupId>] [--package <package>]"
                    + " [--archetype-version <v>] [--studio-api <v>] [--toolkit <v>] [--dir <parent>]");
            return 2;
        }
        String group = args.value("group", "com.example");
        String pkg = args.value("package", group + "." + name.replace("-", "").toLowerCase());
        String archetypeVersion = args.value("archetype-version", "main-SNAPSHOT");
        Path parent = Path.of(args.value("dir", ".")).toAbsolutePath().normalize();
        Path target = parent.resolve(name);
        if (Files.exists(target)) {
            console.error(target + " already exists");
            return 1;
        }
        Files.createDirectories(parent);

        List<String> goals = new ArrayList<>(List.of(
                "archetype:generate",
                "-DinteractiveMode=false",
                "-DarchetypeGroupId=" + ARCHETYPE_GROUP,
                "-DarchetypeArtifactId=" + ARCHETYPE_ARTIFACT,
                "-DarchetypeVersion=" + archetypeVersion,
                "-DgroupId=" + group,
                "-DartifactId=" + name,
                "-Dversion=" + args.value("plugin-version", "0.1.0-SNAPSHOT"),
                "-Dpackage=" + pkg,
                // The archetype's four required properties. pluginId defaults to the groupId and
                // pluginName to the artifactId, which is right for `mvn archetype:generate` typed by hand
                // and wrong here: a plugin id must be unique across the registry, and the groupId alone
                // is not (two plugins from one author share it).
                "-DpluginId=" + args.value("plugin-id", group + "." + name),
                "-DpluginName=" + args.value("plugin-name", title(name)),
                "-DstudioApiVersion=" + args.value("studio-api", DEFAULT_BOTMAKER_VERSION),
                "-DtoolkitVersion=" + args.value("toolkit", DEFAULT_BOTMAKER_VERSION)));

        Mvn.Result result = mvn.runInteractive(parent, goals.toArray(String[]::new));
        if (!result.ok()) {
            console.error("mvn archetype:generate failed");
            return 1;
        }
        console.out("Created " + target);
        console.out("");
        console.out("  cd " + name);
        console.out("  mvn verify         # the generated tests should pass unedited");
        console.out("  botmaker validate  # the same seven checks the registry runs");
        return 0;
    }

    /** {@code discord-notifier} to {@code Discord Notifier} — the name a user reads in Studio. */
    private static String title(String artifactId) {
        StringBuilder out = new StringBuilder();
        for (String word : artifactId.split("[-_.]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? artifactId : out.toString();
    }
}
