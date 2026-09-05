/**
 * {@code release.sh}, ported — the ordered cross-module release, as a library with three callers.
 *
 * <p><b>Why a library and not a command, and not the dashboard.</b> The release has two callers today and
 * will have three: a maintainer's terminal, {@code .github/workflows/release.yml} (which runs the script
 * with {@code --ci}), and {@code botmaker-dashboard}. CI cannot run a JavaFX app, so the owner of these
 * decisions cannot be the GUI. It is the same shape as {@link com.botmaker.cli.validate}, and it is here for
 * the same reason that package is: <i>the check that refuses must be the check its author already ran</i>.
 * So this package <b>prints nothing, spawns no UI and knows no command line</b> — everything that formats a
 * line for a human lives in the command, and everything that decides lives here.
 *
 * <p><b>The port is staged, and no slice ships on being written — it ships on agreeing.</b> A wrong tag is
 * permanent and no exit code recalls one, so each slice is verified by running both implementations'
 * {@code --dry-run} over a matrix of flag combinations and diffing the output. {@code release.sh} keeps
 * cutting the real releases throughout; it is deleted when every slice's diff is empty and one full
 * {@code --all} has been cut through this library.
 *
 * <p>That is also why refusals here carry the script's exact wording (see {@link
 * com.botmaker.cli.release.ReleaseRefusal}): the diff is over stdout, so a rephrased message is a failing
 * slice even when it refuses the same input for the same reason.
 *
 * <h2>What is here so far — slices 1 to 4</h2>
 *
 * <ul>
 *   <li>{@link com.botmaker.cli.release.Module} — the ten modules a tag can be cut for, with the flag
 *       derived from the directory name. This package is the <i>owner</i> of that list, which is why it
 *       keeps one where {@code botmaker-dashboard}, a reader, deliberately does not.</li>
 *   <li>{@link com.botmaker.cli.release.Version} and {@link com.botmaker.cli.release.Level} — the
 *       {@code x.y.z} arithmetic of {@code bump}, ordered as {@code sort -V} orders it.</li>
 *   <li>{@link com.botmaker.cli.release.Tags} — {@code latest_version}: the newest tag a bump is computed
 *       off, fetched first so a release cut elsewhere is not invisible.</li>
 *   <li>{@link com.botmaker.cli.release.VersionSpec} — {@code resolve_version}, as a typed pair rather than
 *       a string every reader re-parses.</li>
 *   <li>{@link com.botmaker.cli.release.Git} — one external command. Only the five algorithms are ported;
 *       everything else stays a process.</li>
 *   <li>{@link com.botmaker.cli.release.Relevance} — {@code is_release_irrelevant}: the deny-list that keeps
 *       a markdown-only diff from cutting a tag whose artifact is byte-identical.</li>
 *   <li>{@link com.botmaker.cli.release.ChangeKind} — {@code change_kind}, three answers rather than a
 *       boolean, because <i>only docs</i> and <i>nothing at all</i> are different things to tell a
 *       maintainer.</li>
 *   <li>{@link com.botmaker.cli.release.ReleaseDecision} — {@code should_release}, carrying its
 *       {@code SKIP_REASON} in the answer instead of a global.</li>
 *   <li>{@link com.botmaker.cli.release.Forcing} — the {@code forced} flags as <b>data with a reason per
 *       edge</b>, which is the one place the port deliberately improves on the script: the reasons record
 *       bugs that shipped, and a shell comment cannot be shown to the operator asking why a module they did
 *       not name is in the plan.</li>
 *   <li>{@link com.botmaker.cli.release.Order} — the decide order and the tag order, which are two
 *       different orders and neither is {@code Module}'s own.</li>
 *   <li>{@link com.botmaker.cli.release.DepTag} — {@code dep_tag}: the ref a downstream pins, which is the
 *       version <i>this run</i> is cutting whenever there is one.</li>
 *   <li>{@link com.botmaker.cli.release.GateVerdict} and {@link com.botmaker.cli.release.GatePlan} — the
 *       gates' four outcomes (a gate that <i>could not run</i> is not a gate that failed) and which module
 *       gets which gate, all of them in the decide pass because a pushed tag cannot be edited.</li>
 *   <li>{@link com.botmaker.cli.release.CiDepsGate} — {@code check_ci_deps}, ported whole: it reads two
 *       files and answers, so there is nothing to shell to.</li>
 *   <li>{@link com.botmaker.cli.release.ChangelogGate} — {@code check_changelog}, which <b>invokes</b> each
 *       module's own {@code tools/changelog-section.sh} rather than reading the file: that extractor has two
 *       readers in two repositories, and a second implementation of it is precisely what it exists to
 *       prevent.</li>
 *   <li>{@link com.botmaker.cli.release.SdkGates} — {@code check_api_pointers} and
 *       {@code check_sdk_plugin}, both invocations: Maven runs one test, and the CLI's own shaded jar
 *       validates the SDK, because <i>this gate and {@code botmaker plugin validate} in an author's
 *       terminal are one program</i>.</li>
 *   <li>{@link com.botmaker.cli.release.JitpackPluginsGate} and
 *       {@link com.botmaker.cli.release.MavenPrerequisite} — {@code check_jitpack_plugins}, the one gate
 *       whose implementation moved rather than being invoked: it was an inline {@code python3} heredoc with
 *       no other reader, so porting it creates no second copy of anything and drops a dependency on
 *       {@code python3} being installed.</li>
 *   <li>{@link com.botmaker.cli.release.Proc} — one external command, which is what most of the script is
 *       and stays.</li>
 * </ul>
 *
 * <p>Still the script's, and not yet callable from here: the decide pass itself (the loop that puts these
 * pieces together and prints the plan); every
 * write ({@code write_deps_env}, {@code stamp_changelog}, {@code commit_tag_push}, the pointer commit —
 * slice 5); and {@code verify_jitpack}, {@code poll_actions} and the release log (slice 6). Nothing in
 * this package pushes anything.
 */
package com.botmaker.cli.release;
