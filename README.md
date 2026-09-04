# botmaker-cli

The **`botmaker`** command: everything a BotMaker plugin author does that is not writing the plugin — and,
under `bot`, everything a bot author does that is not writing the bot.

```bash
botmaker new discord-notifier   # generate a plugin project from the archetype
cd discord-notifier
botmaker validate               # the seven checks the plugin registry runs
botmaker run --project MyBot    # build it, add it to a bot, open Studio on the result
botmaker publish --repo me/discord-notifier
```

```bash
botmaker bot new gamebot                     # a blank bot project — no plugin, no SDK
botmaker bot new farmer --from LiQiyeDev/botmaker-gamebot   # …or somebody's published template
cd gamebot
botmaker bot publish --repo me/gamebot --template --description "A game bot to start from"
```

## Installing it

**Fedora / RHEL** — `botmaker` on your `PATH`, updated with the rest of the system:

```bash
sudo curl -fsSL -o /etc/yum.repos.d/botmaker.repo https://liqiyedev.github.io/botmaker-cli/botmaker.repo
sudo dnf install botmaker
```

**Debian / Ubuntu**:

```bash
sudo install -d -m 755 /etc/apt/keyrings
sudo curl -fsSL -o /etc/apt/keyrings/botmaker.asc https://liqiyedev.github.io/botmaker-cli/botmaker.asc
echo "deb [signed-by=/etc/apt/keyrings/botmaker.asc] https://liqiyedev.github.io/botmaker-cli/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/botmaker.list
sudo apt-get update && sudo apt-get install botmaker
```

Both install one jar at `/usr/share/botmaker/` and a launcher at `/usr/bin/botmaker`, and need a **Java 25**
runtime. Maven is a *recommended* dependency rather than a required one: every verb that resolves a
coordinate shells out to your own `mvn`, and `botmaker bot new` / `bot publish` need none.

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

## The four verbs — about a plugin

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

## `botmaker bot` — about a bot

The verbs above are all about a plugin. This is the other half, and it is the half that had no command at
all: a **starting template** is a published bot, so making one meant a repository, a push, a release and a
hand-written gallery entry, in that order, with nothing failing until somebody else's install 404'd.

### `botmaker bot new <name>`

```bash
botmaker bot new gamebot                                     # blank
botmaker bot new farmer --from LiQiyeDev/botmaker-gamebot     # from a published template
```

**Blank means blank**: a pom, one `main()` that prints a line, and `botmaker-template.properties`. No SDK,
no plugin, no BotMaker API — that is what a project with no plugins installed looks like, and it is one step
from being a bot (**Project ▸ Manage Plugins** in Studio). The repositories are declared, so that step needs
no hand-edited XML.

`--from` downloads that template's release archive and renames **its package** into yours (`--package`,
default `com.<name>`) **and its Maven coordinate** into your project's name. Its entry class keeps the
author's name, its helpers keep theirs, its javadoc keeps its wording: what they shipped is what
demonstrably built for them. The coordinate is the exception because it is not the author's code — it says
which project this is, and a project you called `farm` announcing itself as `base` builds
`base-0.0.1-SNAPSHOT.jar` and collides in `~/.m2` with everyone else's copy of the same template.

### `botmaker bot publish`

```bash
botmaker bot publish --repo me/gamebot --template --description "A game bot to start from"
```

Four steps, each refusing before the next: create the repository and push (a dirty tree is refused; a
directory that is not a repository yet is initialised and committed), cut the release `--tag` names
(default `v0.1.0`), **download that release archive** to check an install can actually fetch it, then fork
`LiQiyeDev/botmaker-gallery`, write `bots/<owner>-<repo>.json` and open the pull request.

`--template` adds the reserved `template` tag, which is what offers the bot in Studio's **New Project**
rather than as something to install and run. Nothing else about a template is different — so anybody can
write one, and it needs no Studio release to appear.

`--dry-run` prints the entry on stdout and does nothing else, so
`botmaker bot publish --dry-run > bots/me-gamebot.json` is the by-hand path when `gh` is not installed.

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
