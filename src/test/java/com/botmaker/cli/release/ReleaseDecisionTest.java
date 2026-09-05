package com.botmaker.cli.release;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseDecisionTest {

    private static final Optional<Version> V116 = Optional.of(new Version(1, 1, 6));
    private static final VersionSpec PATCH = new VersionSpec.Bump(Level.PATCH);
    private static final VersionSpec EXACT = new VersionSpec.Exact(new Version(1, 2, 0));

    @Test
    void aBumpLevelReleasesOnlyOnARealChange() {
        assertTrue(ReleaseDecision.decide(false, false, PATCH, ChangeKind.REAL, V116).releasing());
        assertFalse(ReleaseDecision.decide(false, false, PATCH, ChangeKind.DOCS, V116).releasing());
        assertFalse(ReleaseDecision.decide(false, false, PATCH, ChangeKind.NONE, V116).releasing());
    }

    @Test
    void theThreeWaysToReleaseAnywayBeatEveryChangeKind() {
        for (ChangeKind kind : ChangeKind.values()) {
            assertTrue(ReleaseDecision.decide(true, false, PATCH, kind, V116).releasing(), "--force");
            assertTrue(ReleaseDecision.decide(false, true, PATCH, kind, V116).releasing(), "forced upstream");
            // An exact version is the operator having already answered the question.
            assertTrue(ReleaseDecision.decide(false, false, EXACT, kind, V116).releasing(), "exact");
        }
    }

    @Test
    void theTwoSkipsAreTheScriptsOwnSentences() {
        assertEquals("only docs since v1.1.6 — skipping "
                        + "(the artifact would be identical; --force overrides)",
                ReleaseDecision.decide(false, false, PATCH, ChangeKind.DOCS, V116).skipReason());
        assertEquals("no changes since its latest tag — skipping",
                ReleaseDecision.decide(false, false, PATCH, ChangeKind.NONE, V116).skipReason());
    }

    @Test
    void aReleasedModuleCarriesNoSkipReason() {
        assertEquals("", ReleaseDecision.decide(false, false, PATCH, ChangeKind.REAL, V116).skipReason());
    }

    @Test
    void theDecidePassLineIsIndentedFourSpacesAndNamesTheDirectory() {
        assertEquals("    botmaker-sdk: releasing v1.2.0",
                ReleaseDecision.decide(false, false, EXACT, ChangeKind.NONE, V116)
                        .line(Module.SDK, new Version(1, 2, 0)));
        assertEquals("    botmaker-sdk: no changes since its latest tag — skipping",
                ReleaseDecision.decide(false, false, PATCH, ChangeKind.NONE, V116)
                        .line(Module.SDK, new Version(1, 1, 7)));
    }
}
