package com.botmaker.cli.validate;

import com.botmaker.plugin.api.Capture;
import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.SlotContext;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.TypeRef;
import com.botmaker.plugin.api.ValueContext;

import java.nio.file.Path;
import java.util.List;

/**
 * The contexts {@link PluginValidator} offers a plugin's editor predicates.
 *
 * <p><b>Hand-written here rather than taken from {@code botmaker-plugin-toolkit.testing.TestContexts},
 * which does the same job.</b> The toolkit is a <em>plugin's</em> dependency, resolved onto the plugin's own
 * classloader so that two plugins may hold two versions of it; a host that resolves one version onto its own
 * classpath takes that away. The same rule that forbids {@code botmaker-studio} from listing the toolkit
 * forbids it here, and the cost of keeping it is forty lines.
 *
 * <p>Every service method throws. A predicate is asked <em>which slot is this</em>, and the answer is in the
 * type and the call site it is handed; one that reaches for the theme or a dialog to decide is doing
 * something a headless host cannot support and Studio should not be asked to. The exception message says so,
 * and {@link PluginValidator} reports it as the editor's failure rather than its own.
 */
final class StubContexts {

    private StubContexts() {
    }

    /**
     * A slot: a value of {@code typeName} sitting at argument {@code argIndex} of
     * {@code enclosingClass#enclosingMethod}. This is the shape a call-site-matched editor is looking for,
     * and the shape the archetype's own example uses.
     */
    static SlotContext slot(String typeName, String enclosingClass, String enclosingMethod, int argIndex,
                            String currentSource) {
        return new SlotContext() {
            @Override
            public TypeRef type() {
                return typeRef(typeName);
            }

            @Override
            public List<String> value() {
                return List.of(currentSource == null ? "" : currentSource);
            }

            @Override
            public void set(List<String> value) {
            }

            @Override
            public StudioServices services() {
                return SERVICES;
            }

            @Override
            public String currentSource() {
                return currentSource == null ? "" : currentSource;
            }

            @Override
            public String enclosingClass() {
                return enclosingClass;
            }

            @Override
            public String enclosingMethod() {
                return enclosingMethod;
            }

            @Override
            public int argIndex() {
                return argIndex;
            }

            @Override
            public void replaceWith(String javaExpression, String... importsNeeded) {
            }
        };
    }

    /**
     * A Parameters row: a stored value of {@code typeName} with no call behind it. An editor chosen by the
     * call must decline this one, which is the property the archetype's generated test holds and the reason
     * the validator asks both shapes rather than only the slot.
     */
    static ValueContext row(String typeName, String storedValue) {
        return new ValueContext() {
            @Override
            public TypeRef type() {
                return typeRef(typeName);
            }

            @Override
            public List<String> value() {
                return List.of(storedValue == null ? "" : storedValue);
            }

            @Override
            public void set(List<String> value) {
            }

            @Override
            public StudioServices services() {
                return SERVICES;
            }
        };
    }

    // Not named `type`: a call to it sits inside an anonymous class that already declares `type()`, and
    // Java resolves a method call against the innermost enclosing declaration holding that NAME — arity
    // does not widen the search, so `type(name)` there would not compile.
    private static TypeRef typeRef(String name) {
        return new TypeRef() {
            @Override
            public String simpleName() {
                int dot = name.lastIndexOf('.');
                return dot < 0 ? name : name.substring(dot + 1);
            }

            @Override
            public String qualifiedName() {
                return name;
            }
        };
    }

    private static final StudioServices SERVICES = new StudioServices() {
        @Override
        public Path projectDir() {
            throw unsupported("projectDir()");
        }

        @Override
        public Path resourcesDir() {
            throw unsupported("resourcesDir()");
        }

        @Override
        public Theme theme() {
            throw unsupported("theme()");
        }

        @Override
        public Capture capture() {
            throw unsupported("capture()");
        }

        @Override
        public Dialogs dialogs() {
            throw unsupported("dialogs()");
        }

        private UnsupportedOperationException unsupported(String member) {
            return new UnsupportedOperationException("a slot editor's predicate called StudioServices."
                    + member + "; deciding whether an editor applies must use the slot's type and call site"
                    + " alone, because a host has no project open when it asks");
        }
    };
}
