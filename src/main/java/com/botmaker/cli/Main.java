package com.botmaker.cli;

import java.io.IOException;

/**
 * {@code botmaker} — the entry point.
 *
 * <p>Four verbs, and the shape of the set is the argument for the tool existing at all: {@code new} and
 * {@code publish} are the two ends of a plugin's life, and {@code validate} and {@code run} are the loop in
 * between. Each of them is something an author can do today by hand — {@code mvn archetype:generate} with
 * eight properties, {@code mvn install} then a pom edit then a Studio launch, a hand-written
 * {@code index.json} row — and each is something they will get wrong the first time in a way that produces
 * no error until much later.
 *
 * <p><b>Exit codes are a contract, because CI reads them.</b> {@code 0} success, {@code 1} the thing failed,
 * {@code 2} the command line was wrong. A validation failure is {@code 1} and not {@code 2}: a plugin that
 * does not pass is a well-formed question with a bad answer.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] argv) {
        Args args = Args.parse(argv);
        // Before the verb check: `botmaker --version` has no verb, and answering it with the usage text
        // and exit 2 is how a tool tells a version probe that it is broken.
        if (args.has("version")) {
            System.out.println(version());
            return 0;
        }
        String verb = args.at(0);
        if (verb == null || args.has("help") || "help".equals(verb)) {
            usage();
            return verb == null && !args.has("help") ? 2 : 0;
        }

        Console console = new Console(args.flag("quiet"));
        Mvn mvn = new Mvn(console);
        Subjects subjects = new Subjects(console, mvn);
        warnUnknownOptions(console, verb, args);
        try {
            return switch (verb) {
                case "new" -> new NewCommand(console, mvn).run(args);
                case "validate" -> new ValidateCommand(console, subjects).run(args);
                case "run" -> new RunCommand(console, mvn).run(args);
                case "publish" -> new PublishCommand(console, subjects).run(args);
                default -> {
                    console.error("unknown command '" + verb + "'");
                    usage();
                    yield 2;
                }
            };
        } catch (IOException e) {
            // The message, not the stack: every IOException thrown in this program is thrown with a
            // sentence written for the person reading it, and a trace on top of that sentence buries it.
            // --debug is how you get the trace when the sentence is not enough.
            console.error(e.getMessage());
            if (args.flag("debug")) {
                e.printStackTrace();
            }
            return 1;
        } catch (RuntimeException e) {
            console.error(String.valueOf(e));
            e.printStackTrace();
            return 1;
        }
    }

    /** Options every verb accepts. */
    private static final String[] GLOBAL = {"quiet", "debug", "help", "version"};

    /**
     * Names a mistyped option instead of ignoring it, and does not fail.
     *
     * <p>Every option here has a default, so an unrecognised one changes nothing and produces no error —
     * {@code --botmaker-version 1.2.3} generates a project pinned to something else entirely and says so
     * nowhere. A warning rather than exit 2 because the set below is a list a reader maintains: a real
     * option missing from it must not be able to stop the tool working.
     */
    private static void warnUnknownOptions(Console console, String verb, Args args) {
        String[] known = switch (verb) {
            case "new" -> new String[]{"group", "package", "plugin-id", "plugin-name", "plugin-version",
                    "archetype-version", "studio-api", "toolkit", "dir"};
            case "validate" -> new String[]{"dir", "coordinate", "no-build"};
            case "run" -> new String[]{"dir", "project", "studio", "umbrella", "no-build"};
            case "publish" -> new String[]{"repo", "name", "description", "tags", "min-contract-version",
                    "dir", "dry-run", "no-build"};
            default -> new String[0];
        };
        String[] all = new String[known.length + GLOBAL.length];
        System.arraycopy(known, 0, all, 0, known.length);
        System.arraycopy(GLOBAL, 0, all, known.length, GLOBAL.length);
        for (String unknown : args.unknownOptions(all)) {
            console.warn("ignoring unknown option --" + unknown);
        }
    }

    /**
     * The version, out of the jar's manifest.
     *
     * <p>{@code (dev)} when there is no manifest, which is what an IDE launch and a test run both look like.
     * Not read from a generated constant: this project's poms carry the cosmetic {@code 0.0.0-SNAPSHOT} that
     * JitPack overrides with the tag, so a compiled-in version would be that string forever.
     */
    private static String version() {
        String implementation = Main.class.getPackage().getImplementationVersion();
        return "botmaker " + (implementation == null ? "(dev)" : implementation);
    }

    private static void usage() {
        System.out.println("""
                botmaker — the BotMaker plugin command

                  botmaker new <artifact-id>      generate a plugin project from the archetype
                  botmaker validate [dir]         run the seven checks the plugin registry runs
                  botmaker run                    build the plugin, add it to a project, open Studio
                  botmaker publish                validate, then open the registry pull request

                new
                  --group <groupId>               default com.example
                  --package <package>             default <group>.<name>
                  --plugin-id <id>                the StudioPlugin id; must be unique in the registry
                  --plugin-name <name>            the name a user reads
                  --plugin-version <version>      the generated project's own; default 0.1.0-SNAPSHOT
                  --studio-api <version>          default main-SNAPSHOT
                  --toolkit <version>             default main-SNAPSHOT
                  --archetype-version <version>   the archetype's own; default main-SNAPSHOT
                  --dir <parent>                  where to generate; default .

                validate
                  [dir]                           the plugin project; default .
                  --coordinate <G:A:V>            validate a PUBLISHED artifact instead of a working copy
                  --no-build                      trust the existing target/classes

                run
                  --dir <dir>                     the plugin project; default .
                  --project <name>                a project under ~/BotMakerProjects to add the plugin to
                  --studio <command>              how to launch Studio; or $BOTMAKER_STUDIO
                  --umbrella <dir>                a BotMaker checkout, launched with javafx:run
                  --no-build                      skip `mvn install`

                publish
                  --repo <owner/name>             where the plugin's source lives; required for a real run
                  --name <name>                   overrides the plugin's own displayName
                  --description <text>
                  --tags <a,b,c>
                  --min-contract-version <v>      default: the botmaker-studio-api version in the pom
                  --dry-run                       print the index entry and open nothing

                anywhere
                  --quiet                         no progress on stderr
                  --debug                         print stack traces
                  --version, --help""");
    }
}
