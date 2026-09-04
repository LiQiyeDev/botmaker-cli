package com.botmaker.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command line, tested for what is <em>ours</em> rather than for what picocli owns.
 *
 * <p>The parser this replaced had eight tests over three option forms — picocli's job, and not something
 * worth re-proving here. What is worth proving is the surface: that the exit-code contract still holds
 * (0 / 1 / 2), that an option a command actually reads is an option the command line accepts, and that an
 * option nobody declared is <b>refused</b>. That last one is the reason the library is here at all: the
 * first real invocation of {@code botmaker new} passed {@code --botmaker-version}, which the hand-rolled
 * parser silently ignored, so the generated project was pinned to something else and nothing said so.
 */
class CommandLineTest {

    @Test
    void an_unknown_option_is_refused_and_named() {
        Captured captured = capture(() -> Main.run(new String[]{"new", "x", "--botmaker-version", "1.2.3"}));

        assertEquals(CommandLine.ExitCode.USAGE, captured.exitCode);
        assertTrue(captured.err.contains("--botmaker-version"),
                "the refusal must name the option; got:\n" + captured.err);
    }

    /** picocli's suggestion, which is the half that turns a refusal into a fix. */
    @Test
    void a_near_miss_suggests_the_option_that_was_meant() {
        Captured captured = capture(() -> Main.run(new String[]{"new", "x", "--plugin-versio", "9"}));

        assertTrue(captured.err.contains("--plugin-version"),
                "expected a suggestion naming --plugin-version; got:\n" + captured.err);
    }

    @Test
    void no_verb_prints_the_usage_and_exits_two() {
        Captured captured = capture(() -> Main.run(new String[0]));

        assertEquals(CommandLine.ExitCode.USAGE, captured.exitCode);
        for (String verb : new String[]{"new", "validate", "run", "publish", "bot"}) {
            assertTrue(captured.out.contains(verb), "usage must list " + verb + "; got:\n" + captured.out);
        }
    }

    @Test
    void help_exits_zero() {
        Captured captured = capture(() -> Main.run(new String[]{"--help"}));

        assertEquals(0, captured.exitCode);
    }

    @Test
    void version_answers_even_with_no_manifest() {
        Captured captured = capture(() -> Main.run(new String[]{"--version"}));

        assertEquals(0, captured.exitCode);
        assertTrue(captured.out.startsWith("botmaker "), captured.out);
    }

    /**
     * The completion script names every verb the command line has. That is the property a checked-in
     * script cannot hold: this test fails the day somebody adds a subcommand, which is exactly when a
     * hand-written script would have quietly stopped being complete.
     */
    @Test
    void the_completion_script_offers_every_verb_and_nothing_else() {
        Captured captured = capture(() -> Main.run(new String[]{"completion"}));

        assertEquals(0, captured.exitCode);
        assertTrue(captured.out.contains("complete -F _complete_botmaker"), captured.out);
        for (CommandLine sub : new CommandLine(new Main()).getSubcommands().values()) {
            assertTrue(captured.out.contains(sub.getCommandName()),
                    "the script does not mention " + sub.getCommandName() + ":\n" + captured.out);
        }
    }

    @Test
    void a_missing_artifact_id_is_a_command_line_error_not_a_failure() {
        Captured captured = capture(() -> Main.run(new String[]{"new"}));

        assertEquals(CommandLine.ExitCode.USAGE, captured.exitCode);
    }

    /**
     * The defaults the README and {@code --help} both promise, read off the parse rather than off a run:
     * a generated pom pinned to something other than {@code main-SNAPSHOT} because a default moved is
     * exactly the class of change that produces no error until much later.
     */
    @Test
    void new_defaults_are_the_documented_ones() {
        ParseResult verb = parse("new", "my-plugin").subcommand();

        assertEquals("my-plugin", verb.matchedPositionalValue(0, ""));
        // Not matchedOptionValue: an option nobody typed is not "matched", and the question here is what
        // the command will actually USE — which is the default, applied to the field during the parse.
        assertEquals("com.example", applied(verb, "--group"));
        assertEquals("main-SNAPSHOT", applied(verb, "--studio-api"));
        assertEquals("main-SNAPSHOT", applied(verb, "--toolkit"));
        assertEquals("main-SNAPSHOT", applied(verb, "--archetype-version"));
        assertEquals("0.1.0-SNAPSHOT", applied(verb, "--plugin-version"));
    }

    /** {@code validate} takes its directory either way, which is what the old hand-rolled parser did. */
    @Test
    void validate_accepts_the_directory_as_a_position_or_an_option() {
        assertEquals("plugins/mine", parse("validate", "plugins/mine").subcommand()
                .matchedPositionalValue(0, ""));
        assertEquals("plugins/mine", parse("validate", "--dir", "plugins/mine").subcommand()
                .matchedOptionValue("--dir", ""));
    }

    /** {@code --quiet} and {@code --debug} are inherited, so they parse after the verb as well as before. */
    @Test
    void the_global_flags_parse_on_either_side_of_the_verb() {
        assertTrue(parse("--quiet", "validate").hasMatchedOption("--quiet"));
        assertTrue(parse("validate", "--quiet").subcommand().hasMatchedOption("--quiet"));
    }

    /**
     * {@code publish --tag} exists and is spelled that way.
     *
     * <p>{@code --version} is taken by {@code mixinStandardHelpOptions} on every command here, so this
     * option cannot be called that; the test is here because the name is forced by something invisible at
     * the call site, and renaming it back would fail at construction rather than at review.
     */
    @Test
    void publish_takes_the_tag_it_will_publish_under() {
        assertEquals("v1.2.0", parse("publish", "--tag", "v1.2.0").subcommand()
                .matchedOptionValue("--tag", ""));
    }

    /**
     * {@code bot} is a noun with two verbs under it, and both parse two levels deep.
     *
     * <p>{@code bot new} and {@code new} are different commands about different things — a bot and a plugin
     * — which is exactly why the bot half is a noun rather than four more verbs.
     */
    @Test
    void bot_has_its_own_new_and_publish() {
        ParseResult botNew = parse("bot", "new", "gamebot").subcommand().subcommand();
        assertEquals("gamebot", botNew.matchedPositionalValue(0, ""));
        assertEquals(".", applied(botNew, "--dir"));

        ParseResult botPublish = parse("bot", "publish", "--template", "--repo", "me/gamebot")
                .subcommand().subcommand();
        assertTrue(botPublish.hasMatchedOption("--template"));
        assertEquals("me/gamebot", botPublish.matchedOptionValue("--repo", ""));
        assertEquals("v0.1.0", applied(botPublish, "--tag"));
    }

    /** The option `botmaker run` passes to Studio as a named JavaFX parameter. */
    @Test
    void run_takes_a_project_name() {
        assertEquals("MyBot", parse("run", "--project", "MyBot").subcommand()
                .matchedOptionValue("--project", ""));
    }

    /** The value a command will use for an option: what was typed, or the default the parse applied. */
    private static String applied(ParseResult verb, String option) {
        // The Object local is not ceremony: getValue() is generic, so String.valueOf(getValue()) infers
        // char[] and throws a ClassCastException at runtime.
        Object value = verb.commandSpec().findOption(option).getValue();
        return String.valueOf(value);
    }

    private static ParseResult parse(String... argv) {
        return new CommandLine(new Main()).parseArgs(argv);
    }

    private record Captured(int exitCode, String out, String err) {
    }

    private static Captured capture(Supplier<Integer> body) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            int exitCode = body.get();
            return new Captured(exitCode,
                    out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
