package com.botmaker.cli;

import com.botmaker.cli.project.Poms;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code botmaker plugin run} — build the plugin, put it in a bot project, and open Studio on it.
 *
 * <p><b>The point is that no tag is pushed.</b> A plugin author's inner loop is otherwise: release, wait for
 * JitPack, bump a pin, reopen — for a one-character change to a label. This is the same property the SDK has
 * had all along and for the same reason: {@code mvn install} puts the artifact in {@code ~/.m2}, and Maven
 * checks {@code ~/.m2} before JitPack. Nothing clever, and deliberately nothing new — the local dev loop is
 * the one thing in this project that is not to be reinvented.
 *
 * <p><b>It does not create a bot project.</b> Composing one means composing its pom, which is
 * {@code MavenService}'s job in Studio and stays there for a reason worth repeating: only the thing that
 * knows the whole plugin set can write the file that names them. So this points at a project that already
 * exists, adds one dependency to it, and leaves every other line alone.
 */
@Command(name = "run",
        header = "Build the plugin, add it to a bot project, and open Studio on it.",
        description = "No tag is pushed and nothing is released: mvn install puts the artifact in ~/.m2, "
                + "and Maven checks ~/.m2 before JitPack.",
        mixinStandardHelpOptions = true)
final class PluginRunCommand implements Callable<Integer> {

    /**
     * Where Studio keeps projects. Mirrors {@code studio/config/Constants.PROJECTS_ROOT}.
     *
     * <p>Duplicated rather than imported, because importing it would mean this CLI depends on
     * {@code botmaker-studio} — an app, with JavaFX, OpenCV and JNA behind it — to learn one path. The
     * duplication is safe in the way the repo's own rule allows: it is a constant a user can see and
     * override, not a behaviour two programs must agree on.
     */
    static final Path PROJECTS_ROOT =
            Path.of(System.getProperty("user.home"), "BotMakerProjects");

    @ParentCommand
    private PluginCommand parent;

    @Option(names = "--dir", defaultValue = ".",
            description = "The plugin project. Default: ${DEFAULT-VALUE}")
    private String directory;

    @Option(names = "--project", paramLabel = "<name>",
            description = "A project under ~/BotMakerProjects to add the plugin to.")
    private String projectName;

    @Option(names = "--studio", paramLabel = "<command>",
            description = "How to launch Studio. Or set $BOTMAKER_STUDIO.")
    private String studioCommand;

    @Option(names = "--umbrella", paramLabel = "<dir>",
            description = "A BotMaker umbrella checkout, launched with javafx:run. Or $BOTMAKER_UMBRELLA.")
    private String umbrella;

    @Option(names = "--no-build", description = "Skip `mvn install`.")
    private boolean noBuild;

    @Override
    public Integer call() throws IOException {
        Console console = parent.main().console();
        Path dir = Path.of(directory).toAbsolutePath().normalize();
        Path pluginPom = dir.resolve("pom.xml");
        if (!Files.isRegularFile(pluginPom)) {
            console.error("no pom.xml in " + dir + " — run this from a plugin project, or pass --dir");
            return 2;
        }

        if (!noBuild) {
            console.step("Installing the plugin into ~/.m2…");
            Mvn.Result installed = parent.main().mvn().runInteractive(dir, "install", "-DskipTests");
            if (!installed.ok()) {
                console.error("the plugin did not build");
                return 1;
            }
        }

        Poms.Dependency plugin = Poms.coordinate(pluginPom);
        console.step("Plugin: " + plugin.groupId() + ":" + plugin.artifactId() + ":" + plugin.version());

        if (projectName != null) {
            int added = addToProject(projectName, plugin);
            if (added != 0) {
                return added;
            }
        } else {
            console.warn("no --project given, so nothing was added to a bot's pom. The plugin is in ~/.m2;"
                    + " add it through Project ▸ Manage Libraries, or re-run with --project <name>.");
        }

        return launchStudio();
    }

    /**
     * Adds the plugin to one bot project's pom, and rewrites nothing when it is already there.
     *
     * <p>Idempotence matters more than it sounds: this runs on every launch, and a pom touched every time is
     * a project Studio believes has changed every time.
     */
    private int addToProject(String projectName, Poms.Dependency plugin) throws IOException {
        Console console = parent.main().console();
        Path project = PROJECTS_ROOT.resolve(projectName);
        Path pom = project.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            console.error("no project '" + projectName + "' — expected " + pom);
            console.error("create it in Studio first; composing a bot project's pom is Studio's job,"
                    + " because only it knows the whole plugin set that pom has to name");
            return 1;
        }
        boolean wrote = Poms.upsertDependency(pom, plugin);
        console.step(wrote ? "Added " + plugin.coordinate() + " to " + projectName
                : plugin.coordinate() + " is already in " + projectName);
        return 0;
    }

    /**
     * Starts Studio, however this machine has one.
     *
     * <p>Three ways, in order, and none of them is discovery: a packaged Studio has no canonical location on
     * Linux, so guessing would be worse than asking. {@code --studio} is an explicit command,
     * {@code $BOTMAKER_STUDIO} is the same thing set once, and {@code --umbrella} is the developer's case —
     * an umbrella checkout, run through {@code javafx:run} exactly as {@code CLAUDE.md} documents it.
     */
    private int launchStudio() throws IOException {
        Console console = parent.main().console();
        String projectArg = projectName == null ? null : "--project=" + projectName;

        String checkout = umbrella != null ? umbrella : System.getenv("BOTMAKER_UMBRELLA");
        if (checkout != null && !checkout.isBlank()) {
            List<String> goals = new ArrayList<>(List.of("-pl", "botmaker-studio", "-am", "javafx:run"));
            if (projectArg != null) {
                goals.add("-Djavafx.args=" + projectArg);
            }
            Mvn.Result result = parent.main().mvn().runInteractive(Path.of(checkout), goals.toArray(String[]::new));
            return result.ok() ? 0 : 1;
        }

        String command = studioCommand != null ? studioCommand : System.getenv("BOTMAKER_STUDIO");
        if (command == null || command.isBlank()) {
            console.warn("no Studio to launch. Pass --studio <command>, set BOTMAKER_STUDIO, or point"
                    + " --umbrella at a BotMaker umbrella checkout. The plugin is installed either way.");
            return 0;
        }
        List<String> argv = new ArrayList<>(List.of(command.split("\\s+")));
        if (projectArg != null) {
            argv.add(projectArg);
        }
        console.step("$ " + String.join(" ", argv));
        try {
            return new ProcessBuilder(argv).inheritIO().start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while Studio was running", e);
        }
    }
}
