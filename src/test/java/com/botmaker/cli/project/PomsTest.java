package com.botmaker.cli.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomsTest {

    private static final String PLUGIN_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>my-plugin</artifactId>
                <version>0.1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>com.github.LiQiyeDev</groupId>
                        <artifactId>botmaker-studio-api</artifactId>
                        <version>main-SNAPSHOT</version>
                        <scope>provided</scope>
                    </dependency>
                    <dependency>
                        <groupId>com.github.LiQiyeDev</groupId>
                        <artifactId>botmaker-plugin-toolkit</artifactId>
                        <version>main-SNAPSHOT</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static Path write(Path dir, String xml) throws IOException {
        Path pom = dir.resolve("pom.xml");
        Files.writeString(pom, xml);
        return pom;
    }

    @Test
    void a_declared_scope_is_read_as_declared_and_an_omitted_one_is_empty(@TempDir Path dir)
            throws IOException {
        List<Poms.Dependency> declared = Poms.dependencies(write(dir, PLUGIN_POM));
        assertEquals("provided",
                Poms.find(declared, "com.github.LiQiyeDev", "botmaker-studio-api").orElseThrow().scope());
        // Empty, not "compile": the POM_SCOPES check asks what the file SAYS, and an omitted scope and a
        // declared `compile` are the same to Maven but not to a reader trying to fix one of them.
        assertEquals("",
                Poms.find(declared, "com.github.LiQiyeDev", "botmaker-plugin-toolkit").orElseThrow().scope());
    }

    @Test
    void the_projects_own_coordinate_is_read(@TempDir Path dir) throws IOException {
        Poms.Dependency self = Poms.coordinate(write(dir, PLUGIN_POM));
        assertEquals("com.example", self.groupId());
        assertEquals("my-plugin", self.artifactId());
        assertEquals("0.1.0-SNAPSHOT", self.version());
    }

    @Test
    void a_pom_with_no_dependencies_block_reads_as_none(@TempDir Path dir) throws IOException {
        Path pom = write(dir, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                </project>
                """);
        assertEquals(List.of(), Poms.dependencies(pom));
    }

    @Test
    void adding_a_dependency_writes_it_and_adding_it_again_does_not(@TempDir Path dir) throws IOException {
        Path pom = write(dir, PLUGIN_POM);
        Poms.Dependency plugin = new Poms.Dependency("com.example", "my-plugin", "0.1.0-SNAPSHOT", "");
        assertTrue(Poms.upsertDependency(pom, plugin));
        assertEquals(3, Poms.dependencies(pom).size());
        // The property `botmaker run` depends on: it runs on every launch, and a pom rewritten every time
        // is a project the editor believes has changed every time.
        assertFalse(Poms.upsertDependency(pom, plugin));
    }

    @Test
    void a_changed_version_updates_in_place_rather_than_duplicating(@TempDir Path dir) throws IOException {
        Path pom = write(dir, PLUGIN_POM);
        Poms.upsertDependency(pom, new Poms.Dependency("com.example", "my-plugin", "0.1.0-SNAPSHOT", ""));
        assertTrue(Poms.upsertDependency(pom, new Poms.Dependency("com.example", "my-plugin", "0.2.0", "")));
        List<Poms.Dependency> declared = Poms.dependencies(pom);
        assertEquals(3, declared.size());
        assertEquals("0.2.0", Poms.find(declared, "com.example", "my-plugin").orElseThrow().version());
    }

    @Test
    void a_dependencies_block_is_created_when_the_pom_has_none(@TempDir Path dir) throws IOException {
        Path pom = write(dir, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                </project>
                """);
        assertTrue(Poms.upsertDependency(pom, new Poms.Dependency("x", "y", "1", "")));
        assertEquals(1, Poms.dependencies(pom).size());
    }
}
