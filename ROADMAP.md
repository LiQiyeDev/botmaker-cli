# botmaker-cli — roadmap

## Done

### 2026-09-02 — JDK 25 LTS

`jitpack.yml` → `openjdk25`, the pom to `maven.compiler.release` 25, `javafx.version` → 25.0.4 (still
compile-only — nothing here constructs a `Node`), CI to `java-version: '25'`. The plugin registry's
`validate.yml` moved with it, in its own repository: it resolves this module's main artifact and runs the
validator, so it must never be older than this module's bytecode. Full account in
`../botmaker-studio-api/ROADMAP.md`, dated the same day.

## Now

The four verbs exist and `validate` is a library. What is unproven is the half that needs the other two
repositories:

- **`publish`'s real path is unexercised.** `--dry-run` composes and prints an entry; the `gh` fork → branch
  → commit → PR path is written and has never run, because `LiQiyeDev/botmaker-plugin-registry` does not
  exist yet. It reports the missing `index.json` by name rather than failing obscurely, and that message is
  the thing to check first when the registry lands.
- **`run` has been exercised without Studio.** The install and the pom edit are covered; launching Studio
  through `--umbrella` and `--studio` needs a machine with one.

## Next — and the ordering is not free

1. **The registry** (`botmaker-plugin-registry`, plan phase 8). Its CI resolves this module as a library and
   calls `PluginValidator.validate` with the index's claimed ids filled in. Two of the seven checks
   (`id`, `value-types`) can only ever half-answer here, and the registry is where the other half lives.
2. **`--json` output.** A CI job wants the report as data, not as a terminal rendering. `CheckResult` is
   already a record and Jackson is already a dependency; the only real decision is whether the schema is
   part of this module's semver, and it should be.
3. **A `botmaker doctor`**, or whatever it ends up called: *why does Studio not see my plugin* asked from
   the bot project's side rather than the plugin's. It is the same loader over a different classpath, and it
   is the question an author will actually ask.

## Deliberately not planned

- **A native binary.** JReleaser's `nativeImage` assembler could produce one without changing a single
  command, and the reason not to is that this program's whole job is to shell out to Maven and `gh`. A
  native launcher for a process supervisor buys a hundred milliseconds and costs a per-OS release matrix.
- **Bundling Maven.** The promise of `validate` is that it answers what the author's own build answers.
- **Reading or writing `activities.json`.** That file has one owner and it is the SDK
  (`com.botmaker.sdk.authoring`). A CLI that learned to read it would be a second author of a format whose
  whole design was to have one.
- **Anything that loads a plugin and then reports on what it *does*.** Every check here is about whether a
  plugin works. A check that claimed something about behaviour would read as a safety claim, and nothing in
  this process can keep one.
