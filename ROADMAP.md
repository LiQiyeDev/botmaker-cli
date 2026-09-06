# botmaker-cli — roadmap

## Done

### 2026-09-04 — `sudo dnf install botmaker`

nfpm builds the rpm and the deb from `packaging/nfpm.yaml` in the `release` job, and a `pages` job lifted
from Studio's republishes them as signed dnf/apt repositories. Both packages are small enough to host on
Pages outright, which is the one place this differs from Studio's script (~240 MB there, so its rpm is
metadata-only with a `--baseurl` rewrite).

**Configured on 2026-09-04, and the three things it took are worth writing down, because a repository added
later meets all three again.** `GPG_KEY_ID` / `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` are per-repository
secrets — `LiQiyeDev` is a user account, not an organisation, so Studio's do not carry over and the key has
to be re-exported; `GPG_PRIVATE_KEY` is base64 of the *armored private key* and must be **piped**
(`gpg --export-secret-keys --armor <id> | base64 -w0 | gh secret set …`), because the blob is ~8 KB on one
line and pasting it through a terminal loses part of it — the symptom is
`gpg: no valid OpenPGP data found` in the `pages` job. Enabling Pages creates a `github-pages`
**environment** whose deployment rules allow the default branch only, so a tag-triggered deploy is refused
with `Tag "v0.0.3" is not allowed to deploy to github-pages due to environment protection rules`; the fix is
a second rule of type *tag* matching `v*` (Settings ▸ Environments ▸ `github-pages`), which is what
`botmaker-studio` already had.

### 2026-09-04 — the rpm is signed, because dnf checks two things

`v0.0.3` was the first real install and it failed at the last step: a correctly-signed index over an
unsigned package. `gpgcheck` and `repo_gpgcheck` are independent, the generated `botmaker.repo` sets both,
and only the second was being satisfied. The reasoning that produced the gap is sound and is exactly why it
is recorded rather than quietly patched — `repomd.xml.asc` covers `primary.xml`, which carries the rpm's
checksum, so the index signature *is* a signature over the payload, and **apt accepts that chain**. dnf
does not. nfpm signs the header from `NFPM_RPM_KEY_FILE`, written in the workflow out of the same
`GPG_PRIVATE_KEY` secret; with it unset nfpm builds an unsigned package rather than failing, which is a
fork's tag build. The deb stays unsigned: no ordinary apt verifies a debsigs signature.

### 2026-09-04 — `botmaker bot`, the half of the platform with no command

`bot new` writes the blank project (a pom naming no plugin, one `main`, the template declaration) or
downloads a published template and renames only its package; `bot publish` does the repository, the push,
the release, the archive check and the gallery pull request. The blank composer and the repackager are
deliberate duplicates of Studio's — an application cannot be depended on — and `gallery/GalleryEntry`
mirrors Studio's for the reason `registry/RegistryEntry` mirrors the registry's.

### 2026-09-05 — two nouns, and a `doctor`

The four plugin verbs moved under `botmaker plugin`, so both halves of the platform spell their noun. A
break rather than aliases: v0.x, and a second permanent spelling of every verb costs more than one rename.
The old paths stay as hidden aliases of `MovedCommand`, which runs nothing and names the replacement —
picocli's `Unmatched argument: 'validate'` is the alternative, and it teaches nobody where the verb went.

`doctor` reports Java, Maven, `gh`, `gh auth`, `$BOTMAKER_STUDIO` and the projects root in one pass, with no
network. It adds no capability: every verb already reports its missing tool, halfway through the first real
use. The Maven it names is `Mvn.executable`'s answer, not a second implementation of that precedence.

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

Both nouns exist and `validate` is a library. What is unproven is the half that needs the other two
repositories:

- **`bot publish`'s real path has now run, and it found three defects the tests could not.**
  `LiQiyeDev/botmaker-base` is the first published template (gallery pull request #1, `validate` green): a
  blank project, since a template pinning the SDK would name a plugin the registry does not carry yet.
  What the run caught: `gh repo fork --remote=false` is not a flag when a repository argument is given, so
  **no** pull request either publisher had ever tried to open could have succeeded; the gallery's own
  maintainer cannot fork their own repository, so the submit step needs a direct-branch arm; and a blank
  pom was writing the *publisher's* JVM into a file that travels. The lesson is the one the phase was for —
  a stubbed `gh` proves the arguments are assembled, not that they are accepted.
- **`publish`'s real path is still unexercised.** The same two `gh` fixes landed on it unrun, because
  `LiQiyeDev/botmaker-plugin-registry` does not exist yet. It reports the missing `index.json` by name
  rather than failing obscurely, and that message is the thing to check first when the registry lands.
- **`run` has been exercised without Studio.** The install and the pom edit are covered; launching Studio
  through `--umbrella` and `--studio` needs a machine with one.

## Next — and the ordering is not free

1. **The registry** (`botmaker-plugin-registry`, plan phase 8). Its CI resolves this module as a library and
   calls `PluginValidator.validate` with the index's claimed ids filled in. Two of the eight checks
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
