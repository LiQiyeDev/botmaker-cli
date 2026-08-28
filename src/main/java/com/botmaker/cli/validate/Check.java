package com.botmaker.cli.validate;

/**
 * The seven things asked of a plugin, in the order they are asked.
 *
 * <p><b>An enum rather than seven strings, and the reason is the whole point of this package.</b> Two
 * programs in two repositories report these: the author's {@code botmaker validate} and the registry's CI on
 * a pull request. A check named in one place and spelled differently in the other is a PR that fails for a
 * reason its author could not have seen coming, which is exactly what the gate exists to prevent. So the
 * name and the sentence live here, once, and both readers print what they are given.
 *
 * <p>The order is a dependency order, not a preference: nothing can be said about a plugin's palette until
 * it has loaded, and nothing can be said about anything until the classpath resolves. A run stops at the
 * first {@link Status#FAIL} whose successors would be meaningless, and says so.
 */
public enum Check {

    /** The classpath the plugin would be loaded from resolves, and every entry on it exists. */
    CLASSPATH("classpath", "The plugin's classpath resolves"),

    /** {@code PluginLoader} finds at least one {@code StudioPlugin} on it. */
    LOADS("loads", "ServiceLoader finds a StudioPlugin"),

    /** Every plugin's {@code id()} is well formed, and claimed by nobody else. */
    ID("id", "Every plugin id is well formed and unclaimed"),

    /** {@code catalog(pin).problems()} is empty and every entry names a real public member. */
    PALETTE("palette", "The palette catalog is clean and every entry resolves"),

    /** No {@code valueTypes()} id is blank, duplicated within the subject, or already registered. */
    VALUE_TYPES("value-types", "No value type id collides"),

    /** {@code slotEditors()} builds, and every predicate answers without throwing. */
    EDITORS("editors", "Every slot editor builds and answers"),

    /** {@code botmaker-studio-api} is {@code provided}; {@code botmaker-plugin-toolkit} is not. */
    POM_SCOPES("pom-scopes", "The plugin's dependency scopes are right");

    private final String id;
    private final String title;

    Check(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /** The stable name a report prints and a CI job greps for. Never change one of these. */
    public String id() {
        return id;
    }

    /** One line saying what passing means, phrased as the property that holds. */
    public String title() {
        return title;
    }
}
