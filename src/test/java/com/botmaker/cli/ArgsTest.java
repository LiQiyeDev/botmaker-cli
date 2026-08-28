package com.botmaker.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parser, which is small enough that the only interesting thing about it is the one ambiguity it has to
 * resolve: {@code --key} followed by another {@code --key}.
 */
class ArgsTest {

    @Test
    void positional_arguments_keep_their_order() {
        Args args = Args.parse(new String[]{"validate", "some/dir"});
        assertEquals("validate", args.at(0));
        assertEquals("some/dir", args.at(1));
        assertEquals(List.of("validate", "some/dir"), args.positional());
    }

    @Test
    void a_missing_positional_is_null_rather_than_an_exception() {
        assertEquals(null, Args.parse(new String[]{"validate"}).at(1));
    }

    @Test
    void both_spellings_of_an_option_mean_the_same_thing() {
        assertEquals("x", Args.parse(new String[]{"run", "--project", "x"}).value("project", null));
        assertEquals("x", Args.parse(new String[]{"run", "--project=x"}).value("project", null));
    }

    /**
     * The one case worth a test: an option whose next token is another option is a FLAG, not an option whose
     * value is {@code "--project"}. Getting this wrong makes {@code --dry-run --repo x} swallow the repo.
     */
    @Test
    void an_option_followed_by_another_option_is_a_flag() {
        Args args = Args.parse(new String[]{"publish", "--dry-run", "--repo", "me/thing"});
        assertTrue(args.flag("dry-run"));
        assertEquals("me/thing", args.value("repo", null));
    }

    @Test
    void a_trailing_option_with_no_value_is_a_flag() {
        assertTrue(Args.parse(new String[]{"validate", "--no-build"}).flag("no-build"));
    }

    @Test
    void an_option_with_a_value_is_not_a_flag() {
        Args args = Args.parse(new String[]{"run", "--project", "x"});
        assertTrue(args.has("project"));
        assertFalse(args.flag("project"));
    }

    @Test
    void an_absent_option_falls_back() {
        assertEquals(".", Args.parse(new String[]{"validate"}).value("dir", "."));
    }

    @Test
    void unknown_options_are_reported_by_name() {
        Args args = Args.parse(new String[]{"validate", "--nope", "--dir", "x"});
        assertEquals(List.of("nope"), args.unknownOptions("dir", "coordinate"));
    }
}
