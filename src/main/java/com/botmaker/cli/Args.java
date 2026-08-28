package com.botmaker.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command line, parsed.
 *
 * <p><b>Hand-written rather than picocli, and the reason is the shaded jar.</b> This CLI ships as one
 * executable jar that JBang and {@code java -jar} both run, and every dependency in it is a dependency the
 * plugin registry's CI also downloads to call {@code validate} as a library. Argument parsing is the one
 * thing here small enough that a library costs more than it saves — three forms, sixty lines, no
 * reflection, no annotations, no service loading of its own to collide with the plugin's.
 *
 * <p>Three forms, and nothing else: {@code --key=value}, {@code --key value}, and a bare {@code --flag}.
 * A {@code --key} whose next token starts with {@code --} is a flag, so {@code --dry-run --project x} parses
 * the way it reads. Everything not starting with {@code --} is positional, in order.
 */
public record Args(List<String> positional, Map<String, String> options) {

    /** The value a bare {@code --flag} is stored with. */
    private static final String FLAG = "";

    public Args {
        positional = List.copyOf(positional);
        options = Map.copyOf(options);
    }

    public static Args parse(String[] argv) {
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (!arg.startsWith("--")) {
                positional.add(arg);
                continue;
            }
            String name = arg.substring(2);
            int equals = name.indexOf('=');
            if (equals >= 0) {
                options.put(name.substring(0, equals), name.substring(equals + 1));
            } else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                options.put(name, argv[++i]);
            } else {
                options.put(name, FLAG);
            }
        }
        return new Args(positional, options);
    }

    /** The first positional argument, or {@code null} — the verb, at index 0. */
    public String at(int index) {
        return index < positional.size() ? positional.get(index) : null;
    }

    public boolean has(String option) {
        return options.containsKey(option);
    }

    /** Present with no value, e.g. {@code --dry-run}. A {@code --dry-run=false} is deliberately not a flag. */
    public boolean flag(String option) {
        return FLAG.equals(options.get(option));
    }

    public String value(String option, String fallback) {
        String found = options.get(option);
        return found == null || found.isEmpty() ? fallback : found;
    }

    /** Option names this command did not expect — printed as a warning, never as a failure. */
    public List<String> unknownOptions(String... known) {
        List<String> allowed = List.of(known);
        return options.keySet().stream().filter(name -> !allowed.contains(name)).toList();
    }
}
