package com.botmaker.cli.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseLogTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 9, 5, 12, 12);

    @Test
    void aStudioOnlyReleaseRendersExactlyWhatTheLastRealReleaseWrote() {
        // Byte-for-byte against releases/2026-09-05-1212.md, which release.sh itself wrote.
        String rendered = ReleaseLog.render(WHEN, List.of(
                new ReleaseLog.Row(Module.STUDIO, new Version(1, 0, 37), "", "success (1)", "", "")));

        assertEquals("""
                # Release 2026-09-05 12:12

                | module | version | tag | changelog | jitpack | actions |
                |---|---|---|---|---|---|
                | botmaker-studio | 1.0.37 | v1.0.37 | stamped | n/a (not a Maven artifact) | success (1) |
                """, rendered);
    }

    @Test
    void theTwoModulesNobodyResolvesSaySoRatherThanPending() {
        assertFalse(ReleaseLog.onJitpack(Module.STUDIO));   // an app, packaged per-OS by its own CI
        assertFalse(ReleaseLog.onJitpack(Module.PILOT));    // an APK
        assertTrue(ReleaseLog.onJitpack(Module.SDK));
    }

    @Test
    void everyVerdictStartsPendingBecauseTheLogIsWrittenBeforeThePoll() {
        // A log that only appears after the five-minute poll is missing in exactly the case worth
        // recording: the tags are already pushed by then.
        String rendered = ReleaseLog.render(WHEN, List.of(
                new ReleaseLog.Row(Module.SDK, new Version(1, 1, 7))));

        assertTrue(rendered.contains("| botmaker-sdk | 1.1.7 | v1.1.7 | stamped | pending | pending |"));
    }

    @Test
    void thePilotHasNoChangelogAndTheCellSaysSo() {
        String rendered = ReleaseLog.render(WHEN, List.of(
                new ReleaseLog.Row(Module.PILOT, new Version(0, 0, 12))));

        assertTrue(rendered.contains("| n/a (no CHANGELOG.md) | n/a (not a Maven artifact) | pending |"));
    }

    @Test
    void errorsGoUnderTheTableInFullRatherThanIntoACell() {
        ReleaseLog.Row row = new ReleaseLog.Row(Module.SESSION, new Version(0, 0, 13))
                .withJitpack("BROKEN", "Could not find artifact com.github.LiQiyeDev:botmaker-shared")
                .withActions("FAILED — ci", "ci: failure — https://example.invalid/run/1");

        String rendered = ReleaseLog.render(WHEN, List.of(row));

        assertTrue(rendered.contains("| BROKEN | FAILED — ci |"));
        assertTrue(rendered.contains("## Errors"));
        assertTrue(rendered.contains("**botmaker-session — jitpack**"));
        assertTrue(rendered.contains("Could not find artifact com.github.LiQiyeDev:botmaker-shared"));
        assertTrue(rendered.contains("**botmaker-session — actions**"));
    }

    @Test
    void rowsAreInTagOrderRatherThanTheCallersOwn() {
        Map<Module, Version> released = new EnumMap<>(Module.class);
        released.put(Module.SDK, new Version(1, 1, 7));
        released.put(Module.STUDIO, new Version(1, 0, 38));
        released.put(Module.SHARED, new Version(0, 0, 21));

        assertEquals(List.of(Module.STUDIO, Module.SHARED, Module.SDK),
                ReleaseLog.rows(released).stream().map(ReleaseLog.Row::module).toList());
    }

    @Test
    void theTableIsTheContractAndReadsBackForAReePoll(@TempDir Path umbrella) throws IOException {
        Path log = ReleaseLog.path(umbrella, WHEN);
        Files.createDirectories(log.getParent());
        Files.writeString(log, ReleaseLog.render(WHEN, List.of(
                new ReleaseLog.Row(Module.SHARED, new Version(0, 0, 20)),
                new ReleaseLog.Row(Module.SDK, new Version(1, 1, 6)))));

        // Reading the table back, rather than keeping state beside it, is what lets --status run a week
        // later from another machine on somebody else's release.
        List<ReleaseLog.Row> read = ReleaseLog.read(log);

        assertEquals(2, read.size());
        assertEquals(Module.SHARED, read.get(0).module());
        assertEquals("1.1.6", read.get(1).version().toString());
        assertEquals(log, ReleaseLog.newest(umbrella));
        assertEquals(WHEN, ReleaseStatus.stampOf(log));
    }
}
