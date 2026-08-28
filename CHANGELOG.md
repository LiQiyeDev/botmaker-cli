# Changelog

All notable changes to `botmaker-cli`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this module uses
[semantic versioning](https://semver.org/). `release.sh` refuses to cut a version with no section here.

## [Unreleased]

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
