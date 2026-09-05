package com.botmaker.cli.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionsTest {

    private static final Version V = new Version(1, 1, 7);

    @Test
    void theVerdictIsTheWorstOfEveryRunOnTheTag() {
        // Several workflows fire on one tag — studio's package matrix, its pages deploy and its JReleaser
        // step are three — and one failure is a release whose notes may not exist.
        String tsv = """
                package\tcompleted\tsuccess\thttps://example.invalid/1
                release\tcompleted\tfailure\thttps://example.invalid/2
                pages\tcompleted\tsuccess\thttps://example.invalid/3
                """;

        Actions.Poll poll = Actions.verdict(tsv, V);

        assertEquals("FAILED — release", poll.verdict());
        assertEquals("release: failure — https://example.invalid/2", poll.error());
    }

    @Test
    void aRunStillGoingIsCountedRatherThanCalled() {
        String tsv = """
                package\tin_progress\t\thttps://example.invalid/1
                release\tcompleted\tsuccess\thttps://example.invalid/2
                """;

        assertEquals("running (1 of 2)", Actions.verdict(tsv, V).verdict());
    }

    @Test
    void aSkippedJobIsNotAFailure() {
        // A job that correctly did not apply to this tag has not gone wrong.
        String tsv = "pages\tcompleted\tskipped\thttps://example.invalid/1\n";

        assertEquals("success (1)", Actions.verdict(tsv, V).verdict());
    }

    @Test
    void noRunAtAllIsAFindingBecauseATagIsFinished() {
        // Deliberately unlike a pull request with no check run yet: nothing more will fire on a tag, so
        // this is a verdict rather than a state on the way to one.
        assertEquals("no run on v1.1.7", Actions.verdict("", V).verdict());
        assertTrue(Actions.verdict("", V).error().isEmpty());
    }

    @Test
    void aFailureNamesEveryFailingRunAndItsUrl() {
        String tsv = """
                ci\tcompleted\ttimed_out\thttps://example.invalid/1
                release\tcompleted\tcancelled\thttps://example.invalid/2
                """;

        Actions.Poll poll = Actions.verdict(tsv, V);

        assertEquals("FAILED — ci, release", poll.verdict());
        assertEquals("""
                ci: timed_out — https://example.invalid/1
                release: cancelled — https://example.invalid/2""", poll.error());
    }
}
