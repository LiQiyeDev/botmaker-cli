# Changelog

All notable changes to `botmaker-cli`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this module uses
[semantic versioning](https://semver.org/). `release.sh` refuses to cut a version with no section here.

## [Unreleased]

### Added

- **`botmaker bot` — a second noun, and the half of the platform that had no command.** The four verbs are
  all about a plugin; a *bot*, and in particular a bot published as a starting **template**, had nothing.
  Since a blank project names no plugin (2026-09-04), the richer starting point has to be a published bot
  carrying the `template` tag — and making one meant a repository, a push, a release and a hand-written
  gallery entry, by hand, with no error until an install 404'd on somebody else's machine.
  - **`botmaker bot new <name>`** writes a blank project — a pom, one `main()`, and
    `botmaker-template.properties`. No SDK and no plugin of any kind, which is Studio's own blank shape.
    `--from <owner/repo>` starts from a published template instead: its release archive is downloaded and
    **only its package** is renamed, exactly as Studio's `TemplateProject` does it.
  - **`botmaker bot publish`** creates the repository and pushes, cuts the release, downloads that release
    archive to prove an install can fetch it, then forks the gallery, writes `bots/<owner>-<repo>.json` and
    opens the pull request. `--template` adds the reserved tag. `--dry-run` prints the entry and nothing
    else.
- **`publish --tag <tag>`** — the published version the registry's gate will resolve. Spelled `--tag`
  because `--version` is taken by the standard help mixin, and because what JitPack serves under is a tag.

### Fixed

- **`publish` no longer composes an entry the registry cannot resolve.** The entry's `verifiedVersion` was
  the working pom's `<version>`, which is wrong twice over: a freshly generated plugin is
  `0.1.0-SNAPSHOT` — `botmaker new`'s own default — and JitPack resolves no snapshot, so `botmaker new`
  followed by `botmaker publish` produced a pull request whose gate failed in the *registry's* CI, which is
  the one experience the two-caller design exists to prevent. Deeper: JitPack serves an artifact under the
  **git tag**, and a pom `<version>` matches it only by coincidence. The entry now names the newest tag on
  the working copy, `--tag <tag>` overrides it, and a `-SNAPSHOT` is refused by name with the sentence that
  says what to do.
- **`publish` follows its own pointers before opening a pull request.** The coordinate is resolved through
  `Subjects.fromCoordinate` — literally the gate's first step — and `--repo` is confirmed with
  `gh repo view`. A coordinate nobody can download and a repository nobody can visit are both refusals now,
  and both cost seconds where finding out in CI costs a round trip.

## [0.0.2] — 2026-09-02

### Changed

- **Compiled for Java 25 (LTS).** The executable `botmaker-cli-all.jar` needs a Java 25 runtime, and so does
  the plugin registry's CI, which resolves the main artifact and calls the validator.

### Fixed

- **The executable jar is attached to the release again.** `v0.0.1` published no asset: `jreleaser.yml`
  looked for `target/botmaker-cli-{{projectVersion}}-all.jar`, and shade names the jar after the *pom's*
  version — the cosmetic `0.0.0-SNAPSHOT` that JitPack overrides with the tag — so the path it resolved to,
  `target/botmaker-cli-0.0.1-all.jar`, never existed. The README's
  `releases/latest/download/botmaker-cli-all.jar` install line was a 404 for the whole of `v0.0.1`. The
  workflow now renames the shaded jar to that stable name before JReleaser runs, and fails loudly if shade
  attached nothing, so exactly one name exists from the build onward.

## [0.0.1] — 2026-09-02

First release. `0.x` because the contract and the loader it is built on are both `0.x`, and a release of
either forces a release of this one: flatten bakes both pins into the pom the plugin registry's CI resolves,
and a gate loading plugins with a different loader than Studio's is a gate that admits plugins Studio then
refuses.

**Install:** the executable jar is attached to this release as `botmaker-cli-all.jar`, at a stable name, so
the README's install line can be `releases/latest/download/…`. `com.botmaker.cli.validate` is the **main**
artifact, resolved as a library — see below for why the two are separate.

### Added

- **The module** — the eleventh BotMaker repository, and the first aimed squarely at somebody who does not
  work on BotMaker at all. Four verbs around the plugin platform: `new`, `validate`, `run`, `publish`.
- **`com.botmaker.cli.validate` is a library, and that is the point of the module's shape.** The main
  artifact is the library and the executable jar is the `all` classifier, because the validator has two
  callers in two repositories — the plugin author running `botmaker validate` and the plugin registry's CI
  deciding a pull request — and a submission that fails for a reason its author could not have seen coming
  is exactly what the gate exists to prevent. The package prints nothing, spawns nothing and reaches no
  network: it is handed a `PluginSubject` of resolved facts.
- **The seven checks** — `classpath`, `loads`, `id`, `palette`, `value-types`, `editors`, `pom-scopes`. All
  seven are always reported: a check whose predecessor made it unanswerable is a `SKIP` with the reason,
  never silence, because a report that shrinks when things go wrong hides how much it did not look at.
- **`botmaker new`** — shells to `botmaker-plugin-archetype` and carries no templates of its own. The
  generated shape has exactly one source of truth; three of the things it gets right (the dependency scopes)
  are wrong in ways that produce no compile error.
- **`botmaker validate [dir]`**, and **`--coordinate G:A:V`** for a published artifact. The second is the one
  that catches a plugin which builds on the author's machine and publishes a pom nobody else can resolve —
  the shape of the `0.0.0-SNAPSHOT` bug that shipped in every SDK up to v1.0.24.
- **`botmaker run`** — `mvn install` into `~/.m2`, one idempotent dependency added to a bot project's pom,
  and Studio launched on it. **No tag is pushed**: Maven checks `~/.m2` before JitPack, which is the same
  property the SDK has had all along. It does *not* create a bot project, because composing one means
  composing its pom and only the thing that knows the whole plugin set can write the file that names them.
- **`botmaker publish`** — validates, composes the registry entry from what the plugin already says about
  itself, and opens the pull request with `gh`. `--dry-run` prints the entry and opens nothing. The author
  never hand-edits `index.json`.
- **The registry entry is one file, `plugins/<plugin-id>.json`, and the index is generated from those
  files.** Two authors publishing on the same day open two pull requests with no line in common, and a
  second plugin cannot take an id git already holds a file for — the "already claimed" arm of the `id`
  check becomes a property of the layout rather than of a scan somebody has to remember. Value type ids are
  not filenames, so those still need the scan, and `Registry.claimedValueTypeIds` is what fills the
  `PluginSubject` parameter a local run always leaves empty. An entry carries `verifiedVersion` beside
  `verifiedAt`: a date with no artifact beside it says nothing about what was checked.
- **`com.botmaker.cli.registry.RegistryGate`** — the check that decides a registry pull request, run from
  the registry's CI against this module's **main** artifact. It is here rather than there for the same
  reason the validator is a library at all: it has to be the code the author already ran. It parses two
  positional arguments by hand rather than with picocli, and reads attacker-chosen paths from a file rather
  than from a shell command line.
- **The gate also reserves the ids the host's own plugins own** (`Bundled`, `BOTMAKER_BUNDLED_PLUGINS`).
  A plugin the host *ships* has no entry file, so `com.botmaker.sdk` and the SDK's seventeen value type ids
  were claimed by nobody: a submission taking one passed every check and then lost silently inside
  `ValueCatalog.merge`. The gate resolves the named coordinates and asks the plugins for their ids rather
  than carrying a list that would drift. `Subjects.fromCoordinates` resolves several coordinates onto **one**
  classpath, which is what a host has — the SDK's toolkit dependency is `optional` and so not transitive, and
  `SdkPlugin` cannot be constructed without it.
- **`botmaker publish --dry-run` prints the entry on stdout and nothing else**, so it can be redirected into
  the entry file when `gh` is not installed. The report moved to stderr, which is the rule `Console` already
  stated and this command was breaking.
- **The command line is picocli, and the dependency is `optional`.** `--help` is generated from the same
  annotated fields that parse, so it cannot fall behind them, and an option nobody declared is refused with
  a suggestion instead of ignored. `optional` means it is not transitive: the executable jar carries it and
  the plugin registry, which resolves the main artifact as a *library*, does not — so the rule that
  `com.botmaker.cli.validate` knows no command line is now true of the dependency graph as well as of the
  source.

### Deliberately absent

- **JavaFX.** `javafx-controls` is `provided`, only so `SlotEditor` can be named. That makes the `editors`
  check narrower than it reads — an editor's *predicate* is checked and its *node* is not — and the check
  says so rather than implying otherwise. Carrying JavaFX would make this a per-OS download, and the
  single-jar promise is worth more; the node is seen the first time anybody clicks the slot, which is what
  `botmaker run` is for.
- **`botmaker-plugin-toolkit`.** `StubContexts` is written here rather than using the toolkit's
  `TestContexts`, for the same reason `botmaker-studio` may not depend on the toolkit: it is a *plugin's*
  dependency, resolved onto the plugin's own classloader so that two plugins may hold two versions of it.
- **An embedded Maven resolver.** Maven is shelled to, so that `validate` resolves what the author's own
  build resolves — and so that the registry's CI and the author get the same answer.
- **Any claim about safety.** A plugin runs arbitrary code in Studio's process. Every check here asks
  whether a plugin *works*; none asks whether it is *safe*, and the registry is a curated index with a
  working gate, never a security boundary.
