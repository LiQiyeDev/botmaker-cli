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
 * <h2>What is here so far — slice 1</h2>
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
 * </ul>
 *
 * <p>Still the script's, and not yet callable from here: the decide pass and {@code change_kind}
 * (slice 2), {@code dep_tag}, the forcing rules and the tag order (slice 3), the gates (slice 4), every
 * write — {@code write_deps_env}, {@code stamp_changelog}, {@code commit_tag_push}, the pointer commit
 * (slice 5), and {@code verify_jitpack}, {@code poll_actions} and the release log (slice 6). Nothing in
 * this package pushes anything.
 */
package com.botmaker.cli.release;
