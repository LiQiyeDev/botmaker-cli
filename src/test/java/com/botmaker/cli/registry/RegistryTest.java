package com.botmaker.cli.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-entry layout, and the two things it is for.
 *
 * <p>Uniqueness of the plugin id is git's job — two files cannot share a name — so what is tested here is
 * the half git cannot do: the claimed-id sets the gate feeds into {@code PluginSubject}, and the rule that a
 * file's name is the id inside it.
 */
class RegistryTest {

    @TempDir
    Path root;

    @Test
    void claimed_ids_exclude_the_plugin_being_submitted() throws IOException {
        Path plugins = write(
                entry("com.example.one", List.of("one.channel")),
                entry("com.example.two", List.of("two.colour", "two.shade")));

        Registry registry = Registry.read(plugins);

        // An UPDATE is the case this exists for: a plugin re-verified at a new version still registers the
        // value types it registered before, and counting its own would refuse every plugin its second time.
        assertEquals(Set.of("com.example.two"), registry.claimedPluginIds("com.example.one"));
        assertEquals(Set.of("two.colour", "two.shade"), registry.claimedValueTypeIds("com.example.one"));
        assertEquals(Set.of("one.channel"), registry.claimedValueTypeIds("com.example.two"));
    }

    @Test
    void a_missing_entries_directory_reads_as_an_empty_registry() throws IOException {
        assertTrue(Registry.read(root.resolve("nothing-here")).entries().isEmpty());
    }

    @Test
    void the_filename_is_the_id() throws IOException {
        Path plugins = write(entry("com.example.one", List.of()));
        Registry.Entry only = Registry.read(plugins).entries().getFirst();

        assertEquals(only.entry().id(), only.idFromFilename());
    }

    /** A field a newer `botmaker publish` writes must not make an entry unreadable to an older gate. */
    @Test
    void an_unknown_field_is_ignored_rather_than_refused() throws IOException {
        Path plugins = Files.createDirectories(root.resolve(Registry.ENTRIES_DIRECTORY));
        Files.writeString(plugins.resolve("com.example.future.json"),
                "{\"id\":\"com.example.future\",\"coordinate\":\"g:a\",\"whatIsThis\":42}");

        assertEquals("com.example.future", Registry.read(plugins).entries().getFirst().entry().id());
    }

    @Test
    void the_generated_index_is_an_array_in_id_order() throws IOException {
        Path plugins = write(entry("com.example.two", List.of()), entry("com.example.one", List.of()));

        String index = Registry.read(plugins).index();

        assertTrue(index.startsWith("[") && index.endsWith("]\n"), index);
        assertTrue(index.indexOf("com.example.one") < index.indexOf("com.example.two"), index);
    }

    @Test
    void the_gate_refuses_a_hand_edited_index() {
        assertEquals(1, RegistryGate.run(new String[]{root.toString(), Registry.INDEX}));
    }

    @Test
    void the_gate_resolves_nothing_when_no_entry_changed() {
        assertEquals(0, RegistryGate.run(new String[]{root.toString(), "README.md"}));
    }

    private RegistryEntry entry(String id, List<String> valueTypeIds) {
        return new RegistryEntry(id, id, "com.github.someone:" + id, "someone/" + id, "", List.of(),
                "1.0.0", valueTypeIds, "v1.0.0", "2026-08-28");
    }

    private Path write(RegistryEntry... entries) throws IOException {
        Path plugins = Files.createDirectories(root.resolve(Registry.ENTRIES_DIRECTORY));
        for (RegistryEntry entry : entries) {
            Files.writeString(plugins.resolve(entry.id() + ".json"),
                    Registry.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(entry));
        }
        return plugins;
    }
}
