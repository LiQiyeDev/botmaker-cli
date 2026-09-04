package com.botmaker.cli.gallery;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * One bot's entry in the gallery — {@code bots/<owner>-<repo>.json}.
 *
 * <p><b>A mirror of {@code com.botmaker.studio.sharing.GalleryEntry}, and the mirroring is the same decision
 * {@code registry/RegistryEntry} records.</b> {@code botmaker-studio} is an application rather than a
 * library: depending on it to reuse one record would mean pulling JavaFX, OpenCV and JNA into a command
 * whose whole promise is a single jar. What the two copies must agree on is the <em>file</em> — field names
 * and the path — and that is checked by the thing that reads it, which is Studio.
 *
 * <p><b>{@code launchTargets} is deliberately absent from this copy.</b> Studio's record carries it and
 * reads its absence as {@code SupportedTargets.any()} — <i>the author never said</i>, never <i>works on
 * nothing</i>. Saying which launchers a bot was tested on is something only the person who ran it can know,
 * and the honest answer from a command that has run nothing is silence. An author who wants to declare it
 * publishes from Studio, whose publish dialog asks.
 *
 * <p>Field order is fixed so a pull request adding an entry is one block rather than a reshuffle, and empty
 * values are omitted so an entry with no tags carries no line saying so.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"name", "owner", "repo", "description", "tags"})
public record GalleryEntry(
        /** The project name, PascalCase — also the directory the bot installs into. */
        String name,
        String owner,
        String repo,
        String description,
        List<String> tags) {

    /**
     * The one reserved tag: an entry carrying it is a <b>starting template</b> rather than a bot to install.
     *
     * <p>A template is a published bot — same repo, same release, same install path. That is what lets
     * {@code botmaker bot new --from} hold no template content of its own, and it is why this command can
     * publish one at all: there is no second kind of thing to publish.
     */
    public static final String TEMPLATE_TAG = "template";

    /** Where one entry lives in the gallery repository. The filename is the bot's identity. */
    public static final String ENTRIES_DIRECTORY = "bots";

    public GalleryEntry {
        name = name == null ? "" : name.trim();
        owner = owner == null ? "" : owner.trim();
        repo = repo == null ? "" : repo.trim();
        description = description == null ? "" : description;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** {@code bots/<owner>-<repo>.json} — {@code GitHubConfig.entryPath} in Studio, and it must match. */
    public String path() {
        return ENTRIES_DIRECTORY + "/" + owner + "-" + repo + ".json";
    }

    public String slug() {
        return owner + "/" + repo;
    }

    /**
     * <b>{@code @JsonIgnore}, and it is load-bearing.</b> Jackson reads an {@code isX()} method as a bean
     * property, so without this the entry file carried a {@code "template": true} field of its own beside
     * the tag it is derived from — a second statement of one fact, in the file two repositories agree on.
     */
    @JsonIgnore
    public boolean isTemplate() {
        return tags.stream().anyMatch(TEMPLATE_TAG::equalsIgnoreCase);
    }
}
