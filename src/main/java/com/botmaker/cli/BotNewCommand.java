package com.botmaker.cli;

import com.botmaker.cli.gallery.Templates;
import com.botmaker.cli.project.BlankProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code botmaker bot new <name>} — a bot project, blank or from somebody else's published template.
 *
 * <p><b>Blank means blank: no SDK, no plugin, no BotMaker API.</b> That is not a smaller version of a bot
 * project, it is what a project with no plugins installed looks like, and it is one step from being a bot
 * (<b>Project ▸ Manage Plugins</b>). The platform's rule is that the SDK is one plugin among any number, so
 * a starting point naming it has already made a choice belonging to the person starting.
 *
 * <p><b>{@code --from} holds nothing here.</b> A template is a published bot: the release archive is
 * downloaded, unpacked and repackaged into the caller's own Java package, and nothing else about it is
 * touched. So a new kind of starting point needs no release of this command, and the people who write bots
 * are the people who write the templates.
 */
@Command(name = "new",
        header = "Create a bot project — blank, or from a published template.",
        description = "With no --from this writes a blank project: a pom, one main() and the template "
                + "declaration. With --from it downloads that template's release and renames its package.",
        mixinStandardHelpOptions = true)
final class BotNewCommand implements Callable<Integer> {

    @ParentCommand
    private BotCommand parent;

    @Parameters(index = "0", paramLabel = "<name>",
            description = "The project name. Becomes the artifactId and the entry class's name.")
    private String name;

    @Option(names = "--from", paramLabel = "<owner/repo>",
            description = "Start from a published template instead of a blank project. Accepts owner/repo, "
                    + "or the gallery's own owner-repo spelling.")
    private String from;

    @Option(names = "--tag", paramLabel = "<tag>",
            description = "Which release of --from to take. Default: its newest.")
    private String tag;

    @Option(names = "--package", paramLabel = "<pkg>",
            description = "The Java package. Default: com.<name>, lowercased.")
    private String packageName;

    @Option(names = "--dir", defaultValue = ".", paramLabel = "<path>",
            description = "Where to create it. The project lands in <path>/<name>. Default: ${DEFAULT-VALUE}")
    private String directory;

    @Override
    public Integer call() throws IOException {
        Console console = parent.main().console();
        String pkg = packageName == null || packageName.isBlank()
                ? BlankProject.defaultPackage(name) : packageName.trim();
        BlankProject.requireUsable(name, pkg);

        Path projectDir = Path.of(directory).toAbsolutePath().normalize().resolve(name);
        if (Files.isDirectory(projectDir)) {
            try (var existing = Files.list(projectDir)) {
                if (existing.findAny().isPresent()) {
                    throw new IOException(projectDir + " already exists and is not empty");
                }
            }
        }

        if (from == null || from.isBlank()) {
            BlankProject.write(projectDir, name, pkg);
            console.step("Wrote a blank project in " + projectDir);
        } else {
            fromTemplate(console, projectDir, pkg);
        }

        console.out(projectDir.toString());
        return 0;
    }

    private void fromTemplate(Console console, Path projectDir, String pkg) throws IOException {
        String[] source = ownerAndRepo(from);
        String owner = source[0];
        String repo = source[1];
        String release = tag == null || tag.isBlank() ? Templates.latestReleaseTag(owner, repo) : tag.trim();

        console.step("Downloading " + owner + "/" + repo + "@" + release + "…");
        Templates.downloadInto(owner, repo, release, projectDir);
        Templates.repackage(projectDir, pkg);
        console.step("Unpacked into " + projectDir + " as package " + pkg);
        console.step("Its classes keep the author's names — only the package changed.");
    }

    /**
     * {@code owner/repo}, or the gallery's own {@code owner-repo} filename spelling.
     *
     * <p>The dash form splits at the <b>first</b> dash, because that is what
     * {@code bots/<owner>-<repo>.json} means: a repository name routinely holds dashes
     * ({@code botmaker-gamebot}) and an owner login rarely does. The slash form is unambiguous and is what
     * the help text shows; the dash form exists so an entry filename can be pasted straight in.
     */
    private static String[] ownerAndRepo(String source) throws IOException {
        String trimmed = source.trim();
        int slash = trimmed.indexOf('/');
        if (slash > 0 && slash < trimmed.length() - 1) {
            return new String[]{trimmed.substring(0, slash), trimmed.substring(slash + 1)};
        }
        int dash = trimmed.indexOf('-');
        if (dash > 0 && dash < trimmed.length() - 1) {
            return new String[]{trimmed.substring(0, dash), trimmed.substring(dash + 1)};
        }
        throw new IOException("--from wants owner/repo (or the gallery's owner-repo spelling), got '"
                + source + "'");
    }
}
