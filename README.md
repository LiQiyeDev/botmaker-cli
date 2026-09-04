# botmaker-cli

The **`botmaker`** command: everything a BotMaker plugin author does that is not writing the plugin.

```bash
botmaker new discord-notifier   # generate a project from the archetype
cd discord-notifier
botmaker validate               # the seven checks the plugin registry runs
botmaker run --project MyBot    # build it, add it to a bot, open Studio on the result
botmaker publish --repo me/discord-notifier
```

## Installing it

**With JBang** (no install step, and it keeps itself up to date):

```bash
jbang botmaker@LiQiyeDev validate
```

**Without** — one jar, every OS, no JavaFX inside it:

```bash
curl -LO https://github.com/LiQiyeDev/botmaker-cli/releases/latest/download/botmaker-cli-all.jar
java -jar botmaker-cli-all.jar validate
```

Maven is required and is not bundled: this tool resolves what your own Maven resolves, using your own
settings, because the alternative is a tool that answers a different question from the one your build asks.

**`botmaker --help` and `botmaker <verb> --help` are the reference.** Every option below is described there
too, and there it is generated from the same fields that parse it — so where this file and `--help` ever
disagree, `--help` is right.

## The four verbs

### `botmaker new <artifact-id>`

Runs `botmaker-plugin-archetype` with the prompts answered. It carries **no templates of its own** — the
generated shape has exactly one source of truth, because three of the things it gets right (the dependency
scopes) are wrong in ways that produce no compile error.

```bash
botmaker new discord-notifier --group com.example --plugin-id com.example.discord
```

### `botmaker validate [dir]`

The seven checks, and the same code the registry's CI runs on a pull request:

| check | passes when |
|---|---|
| `classpath` | the plugin's classpath resolves and every entry exists |
| `loads` | `ServiceLoader` finds at least one `StudioPlugin` through the real loader |
| `id` | every id is well formed and claimed by nobody else |
| `palette` | `catalog(pin).problems()` is empty and every entry names a real public member |
| `value-types` | no `ValueType` id collides |
| `editors` | `slotEditors()` builds and every predicate answers without throwing |
| `pom-scopes` | `botmaker-studio-api` is `provided`; `botmaker-plugin-toolkit` is not |

`--coordinate G:A:V` validates a **published** artifact instead of your working copy. That is the one that
catches a plugin which builds on your machine and publishes a pom nobody else can resolve.

**A local pass is not a promise the pull request passes.** Two of the checks ask whether an id is already
claimed, and locally nothing is claimed — the registry's index holds those answers. The report says so.

**And a pass is never a safety claim.** A plugin runs arbitrary code inside Studio's process. Every check
here asks whether a plugin *works*.

### `botmaker run`

`mvn install` into `~/.m2`, add the coordinate to one bot project's pom, launch Studio. **No tag is pushed
and nothing is released** — Maven checks `~/.m2` before JitPack, which is the same property the SDK has had
all along.

```bash
botmaker run --project MyBot --umbrella ~/IdeaProjects/botmaker
botmaker run --project MyBot --studio "/opt/BotMaker/bin/BotMaker"
```

It does **not** create a bot project. Composing one means composing its pom, and only the thing that knows
the whole plugin set can write the file that names them — that is Studio's job and it stays there.

### `botmaker publish`

Validates, composes the registry entry from what the plugin already says about itself, and opens the pull
request with `gh`. `--dry-run` prints the entry and opens nothing.

```bash
botmaker publish --repo me/discord-notifier --description "Sends a message when a bot finishes" \
                 --tags notifications --dry-run
```

**The version in the entry is a git tag, not your pom's `<version>`.** JitPack builds a tag on demand and
serves the artifact under that tag whatever the pom says, so the entry names the newest tag on your working
copy; `--tag v1.2.0` overrides it. A `-SNAPSHOT` is refused here rather than in the registry's CI — a freshly
generated plugin is `0.1.0-SNAPSHOT`, and nobody can download that. Before the pull request is opened, the
coordinate is resolved exactly as the registry's gate will resolve it, and `--repo` is confirmed to exist.

The pull request adds **one file**, `plugins/<plugin-id>.json` — the registry's `index.json` is generated
from those files by its own CI and nobody edits it. So two authors publishing on the same day open two pull
requests with no line in common, and a second plugin cannot take an id git already holds a file for.

`--dry-run`'s stdout is the entry and nothing else, so `botmaker publish --dry-run > plugins/<id>.json` is
the by-hand path when `gh` is not installed. The validation report goes to stderr.

## Using `validate` as a library

The main artifact is a **library**, and the executable jar is the `all` classifier. That split exists for one
reason: `com.botmaker.cli.validate` has two callers in two repositories — the author's `botmaker validate`
and the registry's CI — and a submission that fails for a reason its author could not have seen coming is
exactly what the gate exists to prevent.

```xml
<dependency>
    <groupId>com.github.LiQiyeDev</groupId>
    <artifactId>botmaker-cli</artifactId>
    <version>v0.1.0</version>
</dependency>
```

```java
List<CheckResult> results = PluginValidator.validate(subject);
if (!PluginValidator.passed(results)) { … }
```

The package prints nothing, spawns nothing and reaches no network: it is handed resolved facts. Resolving
them is `com.botmaker.cli.Subjects`, and that is where Maven lives.

## Building

```bash
mvn test        # CommandLineTest, PomsTest, PluginValidatorTest, RegistryTest, BundledTest
mvn install     # com.github.LiQiyeDev:botmaker-cli:0.0.0-SNAPSHOT, plus the -all jar
java -jar target/botmaker-cli-0.0.0-SNAPSHOT-all.jar --help
```

The `-all` jar carries the version in its name because nothing renames it — a `<finalName>` would have
renamed only the *main* artifact and left the pair spelled two different ways. The release renames the asset
once, on the way out (`jreleaser.yml`), so the download URL above stays stable.

Published through JitPack, which serves each git tag under `com.github.LiQiyeDev` regardless of this pom's
`groupId`/`version`. Releases are cut from the umbrella with `../release.sh --cli <version>`.
