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

**picocli is declared `optional`, and that word is doing structural work.** `optional` means *not
transitive*: the shaded `all` jar carries picocli (an optional dependency is on this project's own runtime
classpath, which is what shade packages) and a consumer resolving the main artifact does not — checked with
`dependency:tree` from a throwaway consumer, which lists plugin-host, studio-api and jackson and no picocli.
So the rule above stops being a discipline and becomes a fact about the graph: the registry's CI cannot
accidentally depend on a parser, and `com.botmaker.cli.validate` cannot name one.

It replaced a hand-rolled `Args`, a usage text written as a Java text block and a table naming every option
each verb accepts — **three statements of one fact, which had already disagreed**: the first real
`botmaker new` passed `--botmaker-version`, which was silently ignored and generated a project pinned to
something else. Add an option by adding a field; there is nowhere else to say it.

## Packaging — nfpm, and two things that are refused

`packaging/nfpm.yaml` builds `botmaker.rpm` and `botmaker.deb` from one description in the `release` job;
`.github/scripts/build-repo.sh` republishes them as dnf/apt repositories on GitHub Pages, so
`sudo dnf install botmaker` works and later releases arrive with the system's updates. Contents:
`/usr/share/botmaker/botmaker-cli-all.jar`, `/usr/bin/botmaker` (`packaging/botmaker`, a three-line `exec`),
and the docs. `/usr/bin` on `PATH` is the whole gain over `java -jar` — the jar's directory stops mattering.

**Not jpackage, which is what `botmaker-studio` uses**, and the difference is not a preference: jpackage
bundles a *runtime* into a ~240 MB app-image and generates the launcher, desktop entry, icon and MIME
registration around it, and the same tool has to produce Studio's `.msi` and `.dmg` — one packaging model
for three platforms. This is a 1 MB noarch jar with no desktop presence, so jpackage would generate
nothing worth having and would still force one runner per target OS.

**And not GraalVM `native-image`, structurally rather than by taste**: this program's job is loading
*arbitrary user classes* through a runtime `URLClassLoader` (`PluginLoader.open` over a classpath resolved
at run time). A closed-world image cannot, and no reflection configuration fixes it, because the classes do
not exist at image-build time.

**The packages carry no signature of their own**, and that is deliberate: the Pages repositories sign their
*indexes* (`repomd.xml.asc`, `InRelease`), an index carries the checksum of every package it lists, so the
signature already covers the payload. A package-level signature would verify the same bytes twice and would
need the private key on the build runner as a file.

## `bot` is a noun, and the duplication under it is deliberate

Four verbs about a **plugin**, one noun about a **bot** (2026-09-04). `botmaker bot new` and `botmaker new`
are different commands about different things that share an English word, which is why the bot half is a
noun rather than four more verbs.

`project/BlankProject` re-writes Studio's `MavenService.blankPomXml` + `StarterSources`, and
`gallery/Templates` re-writes its `TemplateProject`; `gallery/GalleryEntry` mirrors
`studio/sharing/GalleryEntry` the way `registry/RegistryEntry` mirrors the registry's. **`botmaker-studio` is
an application, not a library** — depending on it to share forty lines would put JavaFX, OpenCV and JNA
behind a command whose whole promise is a single jar. The precedent and the standing argument are
`validate/StubContexts`'s. What the copies must agree on is *files*, not code: the pom shape (which nothing
maintains after the first commit), and `bots/<owner>-<repo>.json`, which Studio reads.

**`launchTargets` is deliberately missing from this copy of the entry.** Studio reads its absence as *the
author never said*, and the honest declaration from a command that has run no launcher is silence.

**The blank names no plugin**, which is the platform rule reaching project creation: the SDK is one plugin
among any number, so a starting point naming it has chosen for the person starting. The repositories stay so
Manage Plugins can add one without hand-edited XML.

**`bot publish` follows the same rule as `publish`** — the release archive is downloaded before an entry
points at it. A gallery entry with no release behind it is a 404 on somebody else's machine, days later.

## What `publish` puts in an entry, and why none of it is the pom's `<version>`

An entry is a set of pointers, and every one of them is followed before the pull request is opened. That is
the same argument as the validator's: **a submission that fails in the registry's CI for a reason its author
could not have seen coming is what this command exists to prevent**, and a pointer nobody has followed is
exactly such a reason.

`verifiedVersion` is the **newest git tag** (`project/Tags.newest`), not `Poms.coordinate(pom).version()`,
which is what stood there until 2026-09-04. JitPack builds a tag on demand and serves the artifact under
that tag whatever the pom says — this project's own poms carry the cosmetic `0.0.0-SNAPSHOT` for precisely
that reason — so a pom version resolves only where it happens to equal the tag. Worse in the ordinary case:
`botmaker new` generates `0.1.0-SNAPSHOT`, JitPack resolves no snapshot, and so *the first thing a new author
does* produced an entry the gate could not download. A snapshot is now refused here, by name, with the
sentence that says what to do; `--tag` overrides the lookup (`--version` is the help mixin's).

Two more pointers, same shape and same argument: the coordinate is resolved through `Subjects.fromCoordinate`
— the gate's own first step — and `--repo` through `gh repo view`. Both run on a real run only:
`--dry-run` is the by-hand path taken when `gh` is unavailable, so a check needing `gh` cannot be what stops
it. The snapshot refusal is *not* exempted there, because the dry run's stdout is a file the author submits.

## The registry's gate is in this module too

`com.botmaker.cli.registry.RegistryGate` is what `botmaker-plugin-registry`'s CI runs on a pull request. It
lives here for the same reason the validator is a library: **it must be the code the author already ran.**
The registry's workflow resolves this module's *main* artifact and calls the gate; everything the gate adds
on top of `botmaker validate` is what only the registry knows — the ids every other entry claims
(`Registry.claimedValueTypeIds`, which fills the `PluginSubject` parameter a local run always leaves empty),
the ids the **host's own bundled plugins** own (`Bundled`), and the rule that `index.json` is generated. A
check belongs in `PluginValidator`, never here.

**`Bundled` closes a hole the per-entry layout cannot close by itself.** `plugins/<id>.json` makes
entry-vs-entry uniqueness a property of git, but a plugin the host *ships* has no entry file — so
`com.botmaker.sdk` and the SDK's seventeen value type ids were claimed by nobody, and a submission taking one
passed every check and then lost silently in `ValueCatalog.merge`. The gate resolves the coordinates named by
`BOTMAKER_BUNDLED_PLUGINS` and asks the plugins themselves; a hand-kept list of ids here would be a second
answer to a question the SDK already answers, and would drift the first time a type was added. Two details
that are not decoration: the coordinates are resolved onto **one** classpath (`Subjects.fromCoordinates`),
because the SDK's toolkit dependency is `optional` and therefore not transitive and `SdkPlugin` cannot be
constructed without it; and a bundled id is **never** excluded by the submitting entry's own id, where a
registry id is — re-submitting your own plugin is an update, taking the host's is not.

It parses two positional arguments by reading an array, and that is not laziness: picocli is `optional`
precisely so a library consumer resolves no parser, and a gate that needed one would undo it. Changed paths
arrive as `@file` because a pull request chooses its own filenames — a path interpolated into a workflow's
`run:` line is a command injection.

`Subjects` and `Mvn` are public for this one caller, and only `fromCoordinate`/`fromCoordinates` are: a
working copy is the author's question, and the registry has none. `Console.reportAside` exists because `botmaker publish`'s
stdout is the entry JSON — `--dry-run > plugins/<id>.json` has to be a file a parser will read, which is the
same stdout/stderr rule `Console` opens with.

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
mvn test        # CommandLineTest, PomsTest, PluginValidatorTest, RegistryTest
mvn install     # the library and the -all jar
```

`PluginValidatorTest` **compiles its fixtures with javac and loads them through the real `PluginLoader`**,
against `System.getProperty("java.class.path")`. A mocked `StudioPlugin` would pass through the same code and
prove none of it, because it would never have been loaded. Keep it that way; every failure these checks
exist to catch is a failure of a real classloader over real bytecode.

Published through JitPack. Releases are cut from the umbrella with `../release.sh --cli <version>`.
