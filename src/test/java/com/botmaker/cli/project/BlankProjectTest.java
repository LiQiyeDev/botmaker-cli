package com.botmaker.cli.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code botmaker bot new} writes when it is not starting from a template.
 *
 * <p>The load-bearing assertion is {@link #the_blank_project_names_no_plugin_at_all}: "blank" stopped
 * meaning "a bot project with a println in it" on 2026-09-04, in Studio and here, and a pom that quietly
 * reacquired the SDK would make the two starting points differ in the one way that matters.
 */
class BlankProjectTest {

    @TempDir
    Path root;

    @Test
    void the_blank_project_names_no_plugin_at_all() {
        String pom = BlankProject.files("gamebot", "com.gamebot").get("pom.xml");

        assertFalse(pom.contains("botmaker"), "a blank pom names no plugin:\n" + pom);
        assertEquals(1, pom.split("<dependency>", -1).length - 1, "exactly one dependency:\n" + pom);
        assertTrue(pom.contains("junit-jupiter"), pom);
    }

    /**
     * The repositories stay, which is what lets a blank project <em>become</em> a bot without anybody
     * hand-editing XML — Manage Plugins adds the SDK as an ordinary dependency and it resolves.
     */
    @Test
    void the_blank_pom_keeps_the_repositories() {
        String pom = BlankProject.files("gamebot", "com.gamebot").get("pom.xml");

        assertTrue(pom.contains("https://jitpack.io"), pom);
        assertTrue(pom.contains("repo.maven.apache.org"), pom);
    }

    /** The three files, at the paths Studio and {@code TemplateProject} both expect to find them at. */
    @Test
    void the_three_files_are_the_pom_one_main_and_the_template_declaration() {
        Map<String, String> files = BlankProject.files("gamebot", "com.example.game");

        assertEquals(java.util.Set.of("pom.xml",
                        "src/main/java/com/example/game/Gamebot.java",
                        "botmaker-template.properties"),
                files.keySet());
        assertEquals("package=com.example.game\n", files.get("botmaker-template.properties"));
        assertTrue(files.get("src/main/java/com/example/game/Gamebot.java")
                .contains("package com.example.game;"));
    }

    /** The starting file imports nothing, because there is nothing on its classpath to import. */
    @Test
    void the_starting_class_imports_nothing() {
        String main = BlankProject.files("gamebot", "com.gamebot")
                .get("src/main/java/com/gamebot/Gamebot.java");

        assertFalse(main.contains("import "), main);
        assertTrue(main.contains("public static void main(String[] args)"), main);
    }

    @Test
    void names_become_a_class_and_a_package() {
        assertEquals("ClickerBot", BlankProject.className("clicker-bot"));
        assertEquals("Gamebot", BlankProject.className("gamebot"));
        assertEquals("com.clickerbot", BlankProject.defaultPackage("clicker-bot"));
    }

    @Test
    void a_name_maven_cannot_carry_is_refused_before_anything_is_written() {
        assertThrows(IOException.class, () -> BlankProject.requireUsable("my bot", "com.mybot"));
        assertThrows(IOException.class, () -> BlankProject.requireUsable("bot&co", "com.bot"));
        assertThrows(IOException.class, () -> BlankProject.requireUsable("bot", "Com.Bot"));
        assertThrows(IOException.class, () -> BlankProject.requireUsable("bot", "com..bot"));
    }

    @Test
    void writing_lands_every_file_and_refuses_a_directory_that_holds_anything() throws Exception {
        Path project = root.resolve("gamebot");
        BlankProject.write(project, "gamebot", "com.gamebot");

        assertTrue(Files.isRegularFile(project.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(project.resolve("src/main/java/com/gamebot/Gamebot.java")));
        assertTrue(Files.isRegularFile(project.resolve("botmaker-template.properties")));

        assertThrows(IOException.class, () -> BlankProject.write(project, "gamebot", "com.gamebot"),
                "a second write into the same directory must refuse rather than merge");
    }
}
