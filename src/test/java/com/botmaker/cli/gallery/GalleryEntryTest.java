package com.botmaker.cli.gallery;

import com.botmaker.cli.registry.Registry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entry file, which is the whole of the agreement between this command and Studio.
 *
 * <p>Studio's {@code GalleryEntry} is a different class in a different repository — the mirroring is
 * deliberate and recorded on both — so what has to match is the <b>file</b>: its path, its field names, and
 * the reserved tag. These assertions are the written form of that.
 */
class GalleryEntryTest {

    @Test
    void the_path_is_the_one_studio_reads() {
        GalleryEntry entry = new GalleryEntry("GameBot", "LiQiyeDev", "botmaker-gamebot", "", List.of());

        assertEquals("bots/LiQiyeDev-botmaker-gamebot.json", entry.path());
        assertEquals("LiQiyeDev/botmaker-gamebot", entry.slug());
    }

    @Test
    void the_reserved_tag_is_what_makes_an_entry_a_template() {
        assertEquals("template", GalleryEntry.TEMPLATE_TAG);
        assertTrue(new GalleryEntry("G", "o", "r", "", List.of("template")).isTemplate());
        assertTrue(new GalleryEntry("G", "o", "r", "", List.of("Template")).isTemplate());
        assertFalse(new GalleryEntry("G", "o", "r", "", List.of("templates")).isTemplate());
    }

    /**
     * The JSON, field for field. An entry with no tags carries no line saying so, and no field Studio does
     * not know appears — {@code launchTargets} is deliberately absent, which Studio reads as "the author
     * never said".
     */
    @Test
    void the_json_names_what_studio_names_and_nothing_else() throws Exception {
        GalleryEntry entry = new GalleryEntry("GameBot", "LiQiyeDev", "botmaker-gamebot",
                "A game bot to start from", List.of("template", "farming"));

        String json = Registry.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(entry);

        assertTrue(json.contains("\"name\" : \"GameBot\""), json);
        assertTrue(json.contains("\"owner\" : \"LiQiyeDev\""), json);
        assertTrue(json.contains("\"repo\" : \"botmaker-gamebot\""), json);
        assertTrue(json.contains("\"tags\""), json);
        assertFalse(json.contains("launchTargets"), json);
        // isTemplate() is an isX() bean getter, which Jackson serializes unless told not to: without the
        // @JsonIgnore the file carried a "template": true field beside the tag it is derived from.
        assertFalse(json.contains("\"template\" :"), "no field derived from the tags: " + json);

        String bare = Registry.mapper().writeValueAsString(
                new GalleryEntry("Bot", "o", "r", "", List.of()));
        assertFalse(bare.contains("tags"), "an entry with no tags carries no line saying so: " + bare);
        assertFalse(bare.contains("description"), bare);
    }
}
