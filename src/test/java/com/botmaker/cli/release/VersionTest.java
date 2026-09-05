package com.botmaker.cli.release;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionTest {

    @Test
    void bumpMatchesTheScriptsArithmetic() {
        Version v = new Version(1, 2, 3);
        assertEquals("2.0.0", v.bump(Level.MAJOR).toString());
        assertEquals("1.3.0", v.bump(Level.MINOR).toString());
        assertEquals("1.2.4", v.bump(Level.PATCH).toString());
    }

    @Test
    void aModuleWithNoTagBumpsFromZero() {
        // The script's `[[ -z "$cur" ]] && cur="0.0.0"`, which is what makes a first release 0.0.1.
        assertEquals("0.0.1", Version.ZERO.bump(Level.PATCH).toString());
        assertEquals("0.1.0", Version.ZERO.bump(Level.MINOR).toString());
    }

    @Test
    void orderingIsSortDashVAndNotText() {
        // The one way a port of latest_version goes wrong silently: `sort -V` puts 1.10.0 above 1.9.0 and
        // every string comparison puts it below. A wrong answer here is a bump off the wrong base, cut as a
        // tag, which cannot be edited.
        assertTrue(Version.parse("1.10.0").orElseThrow()
                .compareTo(Version.parse("1.9.0").orElseThrow()) > 0);
        assertEquals(Optional.of(new Version(1, 10, 0)),
                Tags.highest(List.of("v1.9.0", "v1.10.0", "v1.2.30")));
    }

    @Test
    void aLeadingVIsStrippedAndAnythingElseIsNotAVersion() {
        assertEquals(Optional.of(new Version(1, 0, 25)), Version.parse("v1.0.25"));
        assertTrue(Version.parse("1.2").isEmpty());
        assertTrue(Version.parse("1.2.0-rc1").isEmpty());
        assertTrue(Version.parse("demo-2026").isEmpty());
        assertTrue(Version.parse(null).isEmpty());
    }

    @Test
    void tagsThatAreNotVersionsAreIgnoredRatherThanRefused() {
        // A repository may carry tags this project did not cut. Stopping a release over one would be
        // refusing on something that cannot change what is published.
        assertEquals(Optional.of(new Version(0, 0, 7)),
                Tags.highest(List.of("demo-2026", "v0.0.7", "nightly")));
        assertTrue(Tags.highest(List.of("demo-2026")).isEmpty());
        assertTrue(Tags.highest(List.of()).isEmpty());
    }

    @Test
    void theTagCarriesTheVAndTheVersionDoesNot() {
        assertEquals("1.2.0", new Version(1, 2, 0).toString());
        assertEquals("v1.2.0", new Version(1, 2, 0).tag());
    }
}
