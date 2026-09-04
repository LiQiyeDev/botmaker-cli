package com.botmaker.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * {@code botmaker bot} — the second noun, and the half of the platform that had no command.
 *
 * <p>The four verbs beside it are all about a <b>plugin</b>: {@code new}, {@code validate}, {@code run},
 * {@code publish}. Every one of them automates something that already had a Studio UI or an obvious
 * {@code mvn} incantation. What had neither is the other half — a <b>bot</b>, and in particular a bot
 * published as a starting <b>template</b>. Since 2026-09-04 a blank project names no plugin, so the richer
 * starting point <em>has</em> to be a published bot carrying {@code template}; and until this command
 * existed, making one meant a repository, a push, a release and a hand-written gallery entry, by hand, in
 * that order, with no error until an install failed on somebody else's machine.
 *
 * <p>A noun rather than four more verbs because {@code new} and {@code publish} already mean something here
 * and mean it about a plugin. {@code botmaker bot new} and {@code botmaker publish} being different commands
 * is the honest reading of two different things that happen to share an English word.
 */
@Command(name = "bot",
        header = "Create and publish a bot project.",
        description = "Start blank or from a published template, then publish the result — repository, "
                + "release and gallery entry — as a template anybody else can start from.",
        mixinStandardHelpOptions = true,
        synopsisSubcommandLabel = "<command>",
        subcommands = {BotNewCommand.class, BotPublishCommand.class})
final class BotCommand implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    Main main() {
        return parent;
    }

    /** No sub-verb: the usage text, and exit 2 — the same contract {@link Main#call()} keeps. */
    @Override
    public Integer call() {
        picocli.CommandLine.usage(this, System.out);
        return picocli.CommandLine.ExitCode.USAGE;
    }
}
