# CLAUDE.md

Guidance for working in **botmaker-cli**, the `botmaker` command.

Read the umbrella `../CLAUDE.md` first, then `../botmaker-plugin-host/CLAUDE.md` (this module's loader) and
`../botmaker-plugin-archetype/CLAUDE.md` (what `botmaker new` generates). The plan this module comes from is
phase 7 of the plugin-ecosystem plan.

## The one structural fact: two artifacts

| artifact | what it is | who consumes it |
|---|---|---|
| `botmaker-cli-<v>.jar` | a **library** — `com.botmaker.cli.validate` and its dependencies | the plugin registry's CI |
| `botmaker-cli-<v>-all.jar` | the **executable** jar (shade, `Main-Class`) | JBang, `java -jar` |

`validate` has two callers in two repositories and they must reach the same verdict, because **a pull
request that fails for a reason its author could not have seen coming is the experience the gate exists to
prevent**. So `com.botmaker.cli.validate` prints nothing, spawns no process, reaches no network and knows no
command line: it is handed a `PluginSubject` of resolved facts. Everything that resolves one — Maven, `gh`,
the filesystem — is in `com.botmaker.cli`.

**If you find yourself adding a rule to `ValidateCommand`, you are adding a rule the registry will not
enforce.** Put it in `PluginValidator`, as a `Check`.

The shade plugin therefore uses `shadedArtifactAttached` rather than replacing the main artifact, and
`createDependencyReducedPom=false` because this module also flattens, and two plugins rewriting one pom is a
race with no winner.

## Why there is no JavaFX in it

`SlotEditor.create` returns `javafx.scene.Node`, so `javafx-controls` is on the **compile** classpath at
`provided` scope — and nothing here ever constructs a `Node`.

That is a real limitation, not a tidy one, and the `EDITORS` check states it: **an editor's predicate is
checked and its node is not.** Building a node needs a live JavaFX toolkit; carrying one would make this a
per-OS download (JavaFX's classes live in platform-classifier jars) and the single-jar promise is worth more.
Even *reaching* `slotEditors()` needs `javafx.scene.Node` on the classpath, because an editor written as a
lambda links its `(ValueContext)Node` method type when the list is built — so with no JavaFX the whole check
skips, saying so.

The half that is checked is the half that decides *which* editor a slot gets. The half that is not is seen
the first time anyone clicks the slot, which is what `botmaker run` exists for.

## Why the stub contexts are written here

`botmaker-plugin-toolkit.testing.TestContexts` does the same job. It is not used, for the same reason
`botmaker-studio` may not depend on the toolkit: **the toolkit is a plugin's dependency, resolved onto the
plugin's own classloader so that two plugins may hold two versions of it.** A host that resolves one version
onto its own classpath takes that away. Forty lines of `StubContexts` is the price of the rule, and it is
cheap.

Every `StudioServices` method on the stub throws, deliberately: a predicate is asked *which slot is this*,
and it has the type and the call site to answer with. One that reaches for the theme is doing something a
headless host cannot support, and the validator reports that as the editor's failure rather than its own.

## Maven is shelled to, never embedded

Maven Resolver as a library would resolve against **its own** idea of the local repository, the mirrors and
the settings — not the user's. The promise of `validate` is that it answers what the registry will answer,
and both keep it by running the build tool the author already has configured. `mvnw` in the project wins over
`$MAVEN_HOME` over `mvn` on the PATH: a project carrying a wrapper has said which Maven it wants.

`dependency:build-classpath` is run at **runtime** scope, and that is load-bearing: the `provided` contract is
absent from a runtime classpath, which is exactly the set a host puts on the loader. A contract that appeared
there would be resolved child-first and become a second `Class` object — the failure `provided` exists to
prevent — and the validator would be testing something no host will ever run.

## What `run` deliberately does not do

It does not create a bot project. Composing one means composing its pom, and **only the thing that knows the
whole plugin set can write the file that names them** — that is `MavenService` in Studio, and the reversal
that put it there (2026-08-26) is recorded in the umbrella `CLAUDE.md`. `run` points at a project that
already exists and adds one dependency, idempotently: it runs on every launch, and a pom rewritten every
time is a project Studio believes has changed every time.

`PROJECTS_ROOT` is duplicated from `studio/config/Constants` rather than imported, because importing it would
mean depending on an app with JavaFX, OpenCV and JNA behind it to learn one path.

Studio is launched through `--umbrella` (`javafx:run`), `--studio`/`$BOTMAKER_STUDIO`, or not at all. No
discovery: a packaged Studio has no canonical location on Linux, and guessing is worse than asking. The
project name reaches Studio as `--project=<name>`, a named JavaFX parameter added to `BotMakerStudio` in the
same phase as this module.

## Building

```bash
mvn test        # ArgsTest, PomsTest, PluginValidatorTest
mvn install     # the library and the -all jar
```

`PluginValidatorTest` **compiles its fixtures with javac and loads them through the real `PluginLoader`**,
against `System.getProperty("java.class.path")`. A mocked `StudioPlugin` would pass through the same code and
prove none of it, because it would never have been loaded. Keep it that way; every failure these checks
exist to catch is a failure of a real classloader over real bytecode.

Published through JitPack. Releases are cut from the umbrella with `../release.sh --cli <version>`.
