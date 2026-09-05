package com.botmaker.cli.release;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionSpecTest {

    @Test
    void aLiteralVersionPassesThroughUntouched() {
        VersionSpec spec = VersionSpec.parse(Module.SDK, "1.2.0");
        assertInstanceOf(VersionSpec.Exact.class, spec);
        // Even against a newer tag: an exact version is the operator saying what they mean.
        assertEquals("1.2.0", spec.against(Optional.of(new Version(9, 9, 9))).toString());
    }

    @Test
    void aLevelIsAppliedToTheModulesOwnLatestTag() {
        assertEquals("1.1.2", VersionSpec.parse(Module.SDK, "patch")
                .against(Optional.of(new Version(1, 1, 1))).toString());
        assertEquals("1.2.0", VersionSpec.parse(Module.SDK, "minor")
                .against(Optional.of(new Version(1, 1, 1))).toString());
        assertEquals("2.0.0", VersionSpec.parse(Module.SDK, "major")
                .against(Optional.of(new Version(1, 1, 1))).toString());
        assertEquals("0.0.1", VersionSpec.parse(Module.SDK, "patch").against(Optional.empty()).toString());
    }

    @Test
    void theRefusalIsTheScriptsOwnSentence() {
        // Character for character: the slice is verified by diffing both implementations' output, so a
        // reworded refusal is a failing diff even though it refuses the same input for the same reason.
        ReleaseRefusal refused = assertThrows(ReleaseRefusal.class,
                () -> VersionSpec.parse(Module.SDK, "1.2"));
        assertEquals("botmaker-sdk: bad version/level '1.2' (want x.y.z or patch|minor|major)",
                refused.getMessage());
        assertEquals("error: botmaker-sdk: bad version/level '1.2' (want x.y.z or patch|minor|major)",
                refused.errorLine());
    }

    @Test
    void aTagNameIsNotAVersionArgument() {
        // Version.parse tolerates a leading v because it also reads tag names; the flag's grammar does not.
        assertThrows(ReleaseRefusal.class, () -> VersionSpec.parse(Module.CLI, "v1.2.0"));
        assertThrows(ReleaseRefusal.class, () -> VersionSpec.parse(Module.CLI, "tiny"));
        assertThrows(ReleaseRefusal.class, () -> VersionSpec.parse(Module.CLI, "PATCH"));
        assertThrows(ReleaseRefusal.class, () -> VersionSpec.parse(Module.CLI, ""));
    }

    @Test
    void aBareFlagMeansPatch() {
        // take_optional gives OPT_VAL="patch" when the next token is another flag or absent.
        assertEquals(new VersionSpec.Bump(Level.PATCH),
                VersionSpec.parse(Module.CLI, Level.DEFAULT.spelling()));
    }
}
