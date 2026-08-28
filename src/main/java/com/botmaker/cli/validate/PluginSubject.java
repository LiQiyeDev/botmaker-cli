package com.botmaker.cli.validate;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * What is being validated, and what the world already claims.
 *
 * <p><b>A record of resolved facts, never a coordinate or a directory.</b> Resolving one of those means
 * running Maven, and Maven is exactly what this package must not know about: the registry's CI resolves a
 * published coordinate and the author's CLI resolves a working copy, and they hand the same shape here so
 * that both get the same verdict. Everything to do with processes, downloads and command lines lives in
 * {@code com.botmaker.cli}.
 *
 * @param classpath         every jar and classes directory the plugin would be loaded from, the plugin's own
 *                          output first. Handed straight to {@code PluginLoader.open}
 * @param pom               the plugin's {@code pom.xml} — the working copy's, or the {@code .pom} resolved
 *                          alongside a published jar. {@code null} when there is none to read, which makes
 *                          {@link Check#POM_SCOPES} a skip rather than a failure
 * @param pinnedVersion     the version to ask {@code catalog(pin)} about, as a project's pom would spell it
 * @param claimedPluginIds  plugin ids the registry already holds, so a submission cannot take one
 * @param claimedValueTypeIds value type ids the registry already holds. Empty when validating locally, which
 *                          is why a clean local run is not a promise the PR will pass — say so in the report
 */
public record PluginSubject(List<Path> classpath, Path pom, String pinnedVersion,
                            Set<String> claimedPluginIds, Set<String> claimedValueTypeIds) {

    public PluginSubject {
        classpath = List.copyOf(classpath);
        pinnedVersion = pinnedVersion == null ? "" : pinnedVersion;
        claimedPluginIds = Set.copyOf(claimedPluginIds);
        claimedValueTypeIds = Set.copyOf(claimedValueTypeIds);
    }

    /** A local run: nothing is claimed yet, because nothing has been submitted. */
    public static PluginSubject local(List<Path> classpath, Path pom, String pinnedVersion) {
        return new PluginSubject(classpath, pom, pinnedVersion, Set.of(), Set.of());
    }
}
