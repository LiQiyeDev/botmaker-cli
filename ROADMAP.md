# botmaker-cli — roadmap

## Done

### 2026-09-04 — `botmaker bot`, the half of the platform with no command

`bot new` writes the blank project (a pom naming no plugin, one `main`, the template declaration) or
downloads a published template and renames only its package; `bot publish` does the repository, the push,
the release, the archive check and the gallery pull request. The blank composer and the repackager are
deliberate duplicates of Studio's — an application cannot be depended on — and `gallery/GalleryEntry`
mirrors Studio's for the reason `registry/RegistryEntry` mirrors the registry's.

### 2026-09-04 — `publish` follows its own pointers

The entry's `verifiedVersion` was the working pom's `<version>` and is now the newest git tag. JitPack
serves an artifact under the tag, so the pom version matched by coincidence at best — and in the ordinary
case not at all: `botmaker new` generates `0.1.0-SNAPSHOT`, which JitPack cannot resolve, so publishing
straight after generating produced a pull request that failed in the registry's CI. `--tag` overrides,
`-SNAPSHOT` is refused by name, the coordinate is resolved through `Subjects.fromCoordinate` before the pull
request is opened, and `--repo` is checked with `gh repo view`.

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
