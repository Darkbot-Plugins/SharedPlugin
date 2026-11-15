#!/usr/bin/env python3
"""Collect the facts needed to review a SharedPlugin change.

Read-only: this script never modifies the repository.

Modes
-----
Uncommitted work (staged + unstaged + untracked):
    python collect_review_context.py --working

A branch compared to its merge-base with a base ref:
    python collect_review_context.py --base origin/main

A GitHub pull request (fetches refs/pull/<n>/head, no gh CLI needed):
    python collect_review_context.py --pr 209

Whole files, when there is no diff to review:
    python collect_review_context.py --files src/main/java/dev/shared/.../Foo.java

Useful flags
------------
--no-diff            skip the unified diff body
--max-diff-lines N   truncate the diff body (default 1500, 0 = no limit)
--json               machine-readable output
--repo PATH          run against another checkout (default: this script's repo)

The output is evidence, not a verdict. Every signal must be confirmed by
reading the surrounding code before it becomes a finding.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

JAVA_DIR = "src/main/java"
PLUGIN_JSON = "src/main/resources/plugin.json"
STRINGS_EN = "src/main/resources/dev/shared/lang/strings_en.properties"

STATUS_NAMES = {
    "A": "added",
    "M": "modified",
    "D": "deleted",
    "R": "renamed",
    "C": "copied",
    "T": "type-changed",
}

# Level is a hint for triage, not a severity decision. The reviewer must
# confirm the context before reporting anything.
SIGNALS = [
    (
        "blocker",
        "generic-catch",
        r"catch\s*\(\s*(?:Exception|Throwable|RuntimeException)\b",
        "Catch the specific exception type; broad catches hide real failures.",
    ),
    (
        "blocker",
        "thread-sleep",
        r"\bThread\s*\.\s*sleep\s*\(",
        "Never block the game thread; use eu.darkbot.util.Timer and re-check next tick.",
    ),
    (
        "blocker",
        "system-exit",
        r"System\s*\.\s*exit\s*\(",
        "Never terminate the JVM from a plugin; stop the bot or its feature instead.",
    ),
    (
        "blocker",
        "external-url",
        r"https?://",
        "Feature data must not come from external URLs (CONTRIBUTING.md).",
    ),
    (
        "major",
        "direct-darkbot-import",
        r"^\s*import\s+com\.github\.manolo8\.darkbot\.",
        "Prefer eu.darkbot.api.*; direct DarkBot internals need justification and isolation.",
    ),
    (
        "major",
        "print-stack-trace",
        r"\.printStackTrace\s*\(",
        "Log with context via System.out.println, not a raw stack trace.",
    ),
    (
        "major",
        "java12-plus",
        r'("""|\brecord\s+[A-Z]\w*\s*\(|\bsealed\s+(?:class|interface)|\bcase\s+[^:;]+->)',
        "Project targets Java 11; records, text blocks, sealed types and switch expressions will not compile.",
    ),
    (
        "major",
        "bot-control",
        r"\b(?:setRunning\s*\(\s*false\s*\)|handleRefresh\s*\(\s*\))",
        "Verify the guards: never stop/refresh while under attack, low HP or captcha is pending.",
    ),
    (
        "major",
        "raw-thread",
        r"new\s+Thread\s*\(",
        "Plugins should use the game/background ticks, not own threads.",
    ),
    (
        "info",
        "system-out",
        r"System\.out\.print",
        "Accepted logging channel in this project, but it must not fire every tick.",
    ),
    (
        "info",
        "foreach-lambda",
        r"\.forEach\s*\(",
        "Lambdas allocate; fine in setup code, only relevant in a per-tick hot path.",
    ),
    (
        "info",
        "synchronized",
        r"\bsynchronized\b",
        "Check that shared state is only touched from one thread, or is guarded.",
    ),
]

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
PUBLIC_TYPE_RE = re.compile(
    r"^\s*public\s+(?:final\s+|abstract\s+)?(?:class|interface|enum|record)\s+(\w+)",
    re.MULTILINE,
)
FEATURE_RE = re.compile(r"^\s*@Feature\s*\(", re.MULTILINE)
OPTION_RE = re.compile(r'@Option\s*\(\s*"(.*?)"\s*\)')
HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


class GitError(RuntimeError):
    pass


def git(*args: str, cwd: Path) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if proc.returncode != 0:
        raise GitError(proc.stderr.strip() or f"git {' '.join(args)} failed")
    return proc.stdout


def try_git(*args: str, cwd: Path) -> str | None:
    try:
        return git(*args, cwd=cwd)
    except GitError:
        return None


def find_root(start: Path) -> Path:
    out = try_git("rev-parse", "--show-toplevel", cwd=start)
    if not out:
        raise SystemExit(f"Not a git repository: {start}")
    return Path(out.strip())


def resolve_commit(root: Path, ref: str) -> str | None:
    out = try_git("rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}", cwd=root)
    return out.strip() if out else None


def default_base(root: Path) -> str:
    for candidate in ("origin/main", "origin/master", "main", "master"):
        if resolve_commit(root, candidate):
            return candidate
    raise SystemExit("Could not find a base branch (tried origin/main, main, master).")


def parse_name_status(output: str) -> list[dict]:
    changes = []
    for raw in output.splitlines():
        if not raw.strip():
            continue
        parts = raw.split("\t")
        code = parts[0][0]
        status = STATUS_NAMES.get(code, code)
        if code in ("R", "C") and len(parts) >= 3:
            changes.append({"status": status, "old_path": parts[1], "path": parts[2]})
        elif len(parts) >= 2:
            changes.append({"status": status, "path": parts[1]})
    return changes


def read_text(root: Path, path: str) -> str:
    try:
        return (root / path).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def file_at_commit(root: Path, commit: str, path: str) -> str:
    out = try_git("show", f"{commit}:{path}", cwd=root)
    return out or ""


def source_text(root: Path, commit: str | None, path: str) -> str:
    """Read a file as it exists at `commit`, or from the working tree when no commit applies."""
    if commit:
        return file_at_commit(root, commit, path)
    return read_text(root, path)


def added_lines_with_numbers(
    root: Path, range_spec: str, path: str
) -> list[tuple[int, str]]:
    """Return (line number in new file, line content) for added lines."""
    diff = try_git(
        "diff", "--no-color", "--no-ext-diff", "-U0", range_spec, "--", path, cwd=root
    )
    if not diff:
        return []
    result: list[tuple[int, str]] = []
    new_line = 0
    for raw in diff.splitlines():
        match = HUNK_RE.match(raw)
        if match:
            new_line = int(match.group(1)) - 1
            continue
        if raw.startswith("+++") or raw.startswith("---"):
            continue
        if raw.startswith("+"):
            new_line += 1
            result.append((new_line, raw[1:]))
        elif raw.startswith(" "):
            new_line += 1
    return result


def parse_json(text: str) -> dict:
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {}


def property_keys_in(text: str) -> set[str]:
    keys: set[str] = set()
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" in line:
            keys.add(line.split("=", 1)[0].strip())
    return keys


def owner_of(path: str) -> str:
    parts = path.replace("\\", "/").split("/")
    if (
        len(parts) > 5
        and parts[0] == "src"
        and parts[1] == "main"
        and parts[2] == "java"
    ):
        if parts[3] == "dev" and parts[4] == "shared" and len(parts) > 6:
            return parts[5]
        return "/".join(parts[3:5])
    if path.startswith("src/main/resources"):
        return "(resources)"
    return "(root)"


def expected_package(path: str) -> str | None:
    marker = JAVA_DIR + "/"
    if marker not in path.replace("\\", "/"):
        return None
    relative = path.replace("\\", "/").split(marker, 1)[1]
    return relative.rsplit("/", 1)[0].replace("/", ".")


def analyse_java(root: Path, path: str, content: str) -> dict:
    package = PACKAGE_RE.search(content)
    public_type = PUBLIC_TYPE_RE.search(content)
    filename = Path(path).stem
    options = [key for key in OPTION_RE.findall(content) if key.strip()]
    return {
        "package": package.group(1) if package else None,
        "public_type": public_type.group(1) if public_type else None,
        "filename": filename,
        "has_feature": bool(FEATURE_RE.search(content)),
        "options": sorted(set(options)),
        "expected_package": expected_package(path),
    }


def build_context(args: argparse.Namespace) -> dict:
    if args.repo:
        start = Path(args.repo).resolve()
    elif try_git("rev-parse", "--show-toplevel", cwd=Path.cwd()):
        start = Path.cwd()
    else:
        start = Path(__file__).resolve().parent
    root = find_root(start)

    mode = (
        "files"
        if args.files
        else "working"
        if args.working
        else "pr"
        if args.pr
        else "base"
    )
    base_ref = None
    head_commit = resolve_commit(root, "HEAD")
    diff_range = None
    untracked: list[str] = []

    if mode == "files":
        changes = []
        for raw_path in args.files:
            candidate = Path(raw_path)
            if candidate.is_absolute():
                try:
                    normalized = candidate.resolve().relative_to(root)
                except ValueError:
                    normalized = candidate
                path = str(normalized).replace("\\", "/")
            else:
                path = raw_path.replace("\\", "/")
            changes.append({"status": "reviewed", "path": path})
    elif mode == "working":
        changes = parse_name_status(
            git("diff", "--name-status", "-M", "HEAD", cwd=root)
        )
        untracked = [
            p
            for p in git(
                "ls-files", "--others", "--exclude-standard", cwd=root
            ).splitlines()
            if p.strip()
        ]
        changes.extend({"status": "untracked", "path": p} for p in untracked)
        diff_range = "HEAD"
    elif mode == "pr":
        local_ref = f"refs/review/pr-{args.pr}"
        fetched = try_git(
            "fetch",
            "--no-tags",
            "origin",
            f"+refs/pull/{args.pr}/head:{local_ref}",
            cwd=root,
        )
        if fetched is None:
            raise SystemExit(
                f"Could not fetch PR #{args.pr}. Check the number and network access, "
                "or pass --base with an already-fetched ref."
            )
        head_commit = resolve_commit(root, local_ref) or local_ref
        base_ref = args.base or default_base(root)
        diff_range = f"{base_ref}...{local_ref}"
        changes = parse_name_status(
            git("diff", "--name-status", "-M", diff_range, cwd=root)
        )
    else:
        base_ref = args.base or default_base(root)
        head_ref = args.head or "HEAD"
        head_commit = resolve_commit(root, head_ref) or head_ref
        diff_range = f"{base_ref}...{head_ref}"
        changes = parse_name_status(
            git("diff", "--name-status", "-M", diff_range, cwd=root)
        )

    base_commit = None
    if base_ref:
        base_commit = resolve_commit(root, base_ref)

    # In pr/base modes the review target is the fetched commit, not the working tree.
    source_commit = head_commit if mode in ("pr", "base") else None

    plugin = parse_json(source_text(root, source_commit, PLUGIN_JSON))
    registered = set(plugin.get("features") or [])
    strings = property_keys_in(source_text(root, source_commit, STRINGS_EN))

    files = []
    signals = []
    missing_registrations = []
    stale_registrations = []
    missing_strings = []
    structural = []

    for change in changes:
        path = change["path"]
        status = change["status"]
        entry = {
            "path": path,
            "status": status,
            "owner": owner_of(path),
            "java": path.endswith(".java"),
        }

        if not path.endswith(".java"):
            files.append(entry)
            continue

        if mode == "files":
            content = read_text(root, path)
            added = [(i + 1, line) for i, line in enumerate(content.splitlines())]
        elif status == "deleted":
            content = file_at_commit(root, base_commit, path) if base_commit else ""
            added = []
        elif status == "untracked":
            content = read_text(root, path)
            added = [(i + 1, line) for i, line in enumerate(content.splitlines())]
        else:
            content = source_text(root, source_commit, path)
            added = added_lines_with_numbers(root, diff_range, path)

        meta = analyse_java(root, path, content)
        entry.update(meta)

        if meta["public_type"] and meta["public_type"] != meta["filename"]:
            structural.append(
                {
                    "level": "major",
                    "rule": "filename-class-mismatch",
                    "path": path,
                    "detail": f"file is {meta['filename']}.java but declares public {meta['public_type']}",
                }
            )

        if meta["expected_package"] and meta["package"] != meta["expected_package"]:
            structural.append(
                {
                    "level": "major",
                    "rule": "package-path-mismatch",
                    "path": path,
                    "detail": f"package is {meta['package']} but path implies {meta['expected_package']}",
                }
            )

        if meta["has_feature"]:
            fqcn = (
                f"{meta['package']}.{meta['filename']}"
                if meta["package"]
                else meta["filename"]
            )
            entry["fqcn"] = fqcn
            if status == "deleted":
                if fqcn in registered:
                    stale_registrations.append(fqcn)
            elif fqcn not in registered:
                missing_registrations.append(fqcn)

        for key in meta["options"]:
            if key not in strings:
                missing_strings.append({"key": key, "path": path})

        for line_no, line in added:
            stripped = line.strip()
            if stripped.startswith(("//", "*", "/*")):
                continue
            for level, rule, pattern, note in SIGNALS:
                if re.search(pattern, line):
                    signals.append(
                        {
                            "level": level,
                            "rule": rule,
                            "path": path,
                            "line": line_no,
                            "text": stripped[:160],
                            "note": note,
                        }
                    )

        files.append(entry)

    diff_text = ""
    if not args.no_diff and diff_range:
        diff_text = (
            try_git("diff", "--no-color", "--no-ext-diff", "-U3", diff_range, cwd=root)
            or ""
        )
    elif not args.no_diff and mode == "files":
        blocks = []
        for change in changes:
            content = read_text(root, change["path"])
            blocks.append(f"--- /dev/null\n+++ b/{change['path']}\n+{content}")
        diff_text = "\n".join(blocks)

    if untracked and not args.no_diff:
        blocks = [diff_text] if diff_text else []
        for path in untracked:
            content = read_text(root, path)
            blocks.append(f"--- /dev/null\n+++ b/{path}\n+{content}")
        diff_text = "\n".join(blocks)

    changed_owners = sorted({f["owner"] for f in files})
    dev_owners = [owner for owner in changed_owners if not owner.startswith("(")]
    return {
        "root": str(root),
        "mode": mode,
        "base_ref": base_ref,
        "base_commit": base_commit,
        "head_commit": head_commit,
        "diff_range": diff_range,
        "changes": files,
        "changed_owners": changed_owners,
        "multi_owner": len(dev_owners) > 1,
        "signals": signals,
        "missing_registrations": missing_registrations,
        "stale_registrations": stale_registrations,
        "missing_strings": missing_strings,
        "structural": structural,
        "plugin_json_located": bool(plugin),
        "strings_en_exists": bool(strings),
        "diff": diff_text,
        "diff_truncated": False,
    }


def render_markdown(context: dict, max_diff_lines: int) -> str:
    out: list[str] = []
    add = out.append

    add("# Review context")
    add("")
    add(f"- Repository: `{context['root']}`")
    add(f"- Mode: `{context['mode']}`")
    if context["base_ref"]:
        add(f"- Base ref: `{context['base_ref']}`")
    if context["diff_range"]:
        add(f"- Diff range: `{context['diff_range']}`")
    add(f"- Files changed: {len(context['changes'])}")
    owners = ", ".join(context["changed_owners"]) or "none"
    add(f"- Owners touched: {owners}")
    if context["multi_owner"]:
        add(
            "- **Multi-owner change**: confirm every affected developer agrees with it."
        )
    add("")

    add("## Changed files")
    add("")
    add("| Status | File | Owner | Notes |")
    add("| --- | --- | --- | --- |")
    for change in context["changes"]:
        notes = []
        if change.get("fqcn"):
            notes.append(f"`@Feature` {change['fqcn']}")
        if change.get("options"):
            notes.append(f"{len(change['options'])} @Option key(s)")
        add(
            f"| {change['status']} | `{change['path']}` | {change['owner']} | "
            f"{'; '.join(notes) or ''} |"
        )
    add("")

    add("## Registration and i18n")
    add("")
    if not context["plugin_json_located"]:
        add("- WARNING: could not parse `plugin.json`.")
    if not context["strings_en_exists"]:
        add("- WARNING: could not read `strings_en.properties`.")
    if context["missing_registrations"]:
        for fqcn in context["missing_registrations"]:
            add(f"- [MISSING] `{fqcn}` has `@Feature` but is not in `plugin.json`.")
    if context["stale_registrations"]:
        for fqcn in context["stale_registrations"]:
            add(
                f"- [STALE] `{fqcn}` is still in `plugin.json` but its class was deleted."
            )
    if context["missing_strings"]:
        for item in context["missing_strings"]:
            add(f"- [MISSING STRING] `{item['key']}` (from `{item['path']}`).")
    if not (
        context["missing_registrations"]
        or context["stale_registrations"]
        or context["missing_strings"]
    ):
        add(
            "- All changed `@Feature` classes are registered and every changed `@Option` key exists in English strings."
        )
    add("")

    add("## Structural checks")
    add("")
    if context["structural"]:
        for item in context["structural"]:
            add(
                f"- [{item['level'].upper()}] {item['rule']} — `{item['path']}`: {item['detail']}"
            )
    else:
        add("- No filename/class or package/path mismatch detected.")
    add("")

    add("## Signals in added lines")
    add("")
    add("These are regex hits, not findings. Confirm the surrounding code first.")
    add("")
    if context["signals"]:
        add("| Hint | Rule | Location | Line | Note |")
        add("| --- | --- | --- | --- | --- |")
        for signal in context["signals"]:
            text = signal["text"].replace("|", "\\|")
            add(
                f"| {signal['level']} | {signal['rule']} | `{signal['path']}:{signal['line']}` | "
                f"`{text}` | {signal['note']} |"
            )
    else:
        add("- No signals matched.")
    add("")

    if max_diff_lines and context["diff"]:
        lines = context["diff"].splitlines()
        if len(lines) > max_diff_lines:
            context["diff"] = "\n".join(lines[:max_diff_lines])
            context["diff_truncated"] = True

    if context["diff"]:
        add("## Diff")
        add("")
        if context["diff_truncated"]:
            add(
                f"_Truncated at {max_diff_lines} lines. Re-run with --max-diff-lines 0 for the full diff._"
            )
            add("")
        add("```diff")
        add(context["diff"])
        add("```")
        add("")

    return "\n".join(out)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--working",
        action="store_true",
        help="review staged, unstaged and untracked work",
    )
    mode.add_argument(
        "--base", help="review HEAD against this base ref (three-dot diff)"
    )
    parser.add_argument(
        "--head",
        default="HEAD",
        help="upper bound for --base (default HEAD); use to review a single commit",
    )
    mode.add_argument("--pr", type=int, help="review a GitHub pull request by number")
    mode.add_argument(
        "--files", nargs="+", help="review these files in full, ignoring git"
    )
    parser.add_argument(
        "--repo", help="path to the checkout (default: the repo containing this script)"
    )
    parser.add_argument("--no-diff", action="store_true", help="omit the diff body")
    parser.add_argument(
        "--max-diff-lines",
        type=int,
        default=1500,
        help="truncate the diff (0 = no limit)",
    )
    parser.add_argument(
        "--json", action="store_true", help="emit JSON instead of markdown"
    )
    args = parser.parse_args(argv)

    context = build_context(args)
    if args.json:
        print(json.dumps(context, indent=2, ensure_ascii=False))
    else:
        print(render_markdown(context, args.max_diff_lines))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
