#!/usr/bin/env python3
"""Build GitHub Wiki pages from repository documentation.

The repository README and docs/*.md files remain the source of truth. This
script produces build/wiki with GitHub Wiki friendly page names and rewritten
relative links.
"""

from __future__ import annotations

import re
import shutil
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "build" / "wiki"


@dataclass(frozen=True)
class WikiPage:
    source: str
    title: str
    target: str
    group: str


WIKI_PAGES = [
    WikiPage("README.md", "Project Overview", "Project-Overview.md", "Start Here"),
    WikiPage("docs/getting-started.md", "Getting Started", "Getting-Started.md", "Start Here"),
    WikiPage("docs/cli.md", "CLI Reference", "CLI-Reference.md", "User Guides"),
    WikiPage("docs/configuration.md", "Configuration Guide", "Configuration-Guide.md", "User Guides"),
    WikiPage("docs/config-reference.md", "Configuration Reference", "Configuration-Reference.md", "User Guides"),
    WikiPage("docs/experiment-cookbook.md", "Experiment Cookbook", "Experiment-Cookbook.md", "User Guides"),
    WikiPage("docs/algorithms.md", "Algorithms Guide", "Algorithms-Guide.md", "Scheduling"),
    WikiPage("docs/realtime-scheduling.md", "Realtime Scheduling", "Realtime-Scheduling.md", "Scheduling"),
    WikiPage("docs/realtime-metrics.md", "Realtime Metrics", "Realtime-Metrics.md", "Scheduling"),
    WikiPage("docs/google-trace.md", "Google Trace", "Google-Trace.md", "Data And Results"),
    WikiPage("docs/results-and-analysis.md", "Results And Analysis", "Results-And-Analysis.md", "Data And Results"),
    WikiPage("docs/performance.md", "Performance Guide", "Performance-Guide.md", "Data And Results"),
    WikiPage("docs/container.md", "Container Guide", "Container-Guide.md", "Operations"),
    WikiPage("docs/architecture.md", "Architecture", "Architecture.md", "Development"),
    WikiPage("docs/api.md", "API And Extension Guide", "API-And-Extension-Guide.md", "Development"),
    WikiPage("docs/development.md", "Development Guide", "Development-Guide.md", "Development"),
    WikiPage("docs/testing.md", "Testing Guide", "Testing-Guide.md", "Development"),
    WikiPage("docs/build-logic.md", "Build Logic", "Build-Logic.md", "Development"),
    WikiPage("docs/ci-workflows.md", "CI Workflows", "CI-Workflows.md", "Operations"),
    WikiPage("docs/supply-chain.md", "Supply Chain", "Supply-Chain.md", "Operations"),
    WikiPage("docs/release-package.md", "Release Package", "Release-Package.md", "Operations"),
    WikiPage("docs/release-readiness.md", "Release Readiness", "Release-Readiness.md", "Operations"),
    WikiPage("docs/troubleshooting.md", "Troubleshooting", "Troubleshooting.md", "Start Here"),
    WikiPage("docs/glossary.md", "Glossary", "Glossary.md", "Start Here"),
    WikiPage("docs/wiki-sync.md", "Wiki Sync", "Wiki-Sync.md", "Operations"),
]


def main() -> None:
    validate_sources()
    rebuild_output_dir()
    link_map = build_link_map()
    write_home()
    write_sidebar()
    for page in WIKI_PAGES:
        write_page(page, link_map)
    print(f"Generated {len(WIKI_PAGES) + 2} wiki files in {OUTPUT_DIR}")


def validate_sources() -> None:
    targets = [page.target for page in WIKI_PAGES]
    duplicate_targets = sorted({target for target in targets if targets.count(target) > 1})
    if duplicate_targets:
        raise SystemExit(f"Duplicate wiki targets: {', '.join(duplicate_targets)}")

    missing = [page.source for page in WIKI_PAGES if not (ROOT / page.source).is_file()]
    if missing:
        raise SystemExit(f"Missing wiki source files: {', '.join(missing)}")


def rebuild_output_dir() -> None:
    if OUTPUT_DIR.exists():
        shutil.rmtree(OUTPUT_DIR)
    OUTPUT_DIR.mkdir(parents=True)


def build_link_map() -> dict[str, str]:
    mapping: dict[str, str] = {
        "README.md": "Project-Overview",
        "./README.md": "Project-Overview",
    }
    for page in WIKI_PAGES:
        source = page.source.replace("\\", "/")
        target = page.target.removesuffix(".md")
        mapping[source] = target
        mapping[f"./{source}"] = target
        if source.startswith("docs/"):
            short = source.removeprefix("docs/")
            mapping[short] = target
            mapping[f"./{short}"] = target
    return mapping


def write_home() -> None:
    lines = [
        "# CloudSim-Benchmark Wiki",
        "",
        "This wiki is generated from the repository README and `docs/` directory.",
        "Edit the source documents in the main repository, not the wiki pages directly.",
        "",
        "## Start Here",
        "",
        "- [Project Overview](Project-Overview)",
        "- [Getting Started](Getting-Started)",
        "- [Troubleshooting](Troubleshooting)",
        "- [Glossary](Glossary)",
        "",
        "## Complete Index",
        "",
    ]
    for group in ordered_groups():
        lines.append(f"### {group}")
        lines.append("")
        for page in pages_for_group(group):
            lines.append(f"- [{page.title}]({page.target.removesuffix('.md')})")
        lines.append("")
    (OUTPUT_DIR / "Home.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def write_sidebar() -> None:
    lines = ["# CloudSim-Benchmark", ""]
    for group in ordered_groups():
        lines.append(f"## {group}")
        for page in pages_for_group(group):
            lines.append(f"- [{page.title}]({page.target.removesuffix('.md')})")
        lines.append("")
    (OUTPUT_DIR / "_Sidebar.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def write_page(page: WikiPage, link_map: dict[str, str]) -> None:
    content = (ROOT / page.source).read_text(encoding="utf-8")
    content = rewrite_markdown_links(content, link_map)
    footer = (
        "\n\n---\n\n"
        f"_Generated from `{page.source}`. Do not edit this wiki page directly; "
        "change the repository source document instead._\n"
    )
    (OUTPUT_DIR / page.target).write_text(content.rstrip() + footer, encoding="utf-8")


def rewrite_markdown_links(content: str, link_map: dict[str, str]) -> str:
    def replace(match: re.Match[str]) -> str:
        label = match.group(1)
        destination = match.group(2)
        rewritten = rewrite_destination(destination, link_map)
        return f"[{label}]({rewritten})"

    return re.sub(r"\[([^\]]+)]\(([^)]+)\)", replace, content)


def rewrite_destination(destination: str, link_map: dict[str, str]) -> str:
    if is_external_or_anchor(destination):
        return destination

    path, anchor = split_anchor(destination)
    normalized = path.replace("\\", "/").lstrip("/")
    while normalized.startswith("../"):
        normalized = normalized[3:]
    target = link_map.get(normalized)
    if target is None:
        return destination
    return f"{target}{anchor}"


def is_external_or_anchor(destination: str) -> bool:
    return (
        destination.startswith("#")
        or "://" in destination
        or destination.startswith("mailto:")
        or destination.startswith("file:")
    )


def split_anchor(destination: str) -> tuple[str, str]:
    if "#" not in destination:
        return destination, ""
    path, anchor = destination.split("#", 1)
    return path, f"#{anchor}"


def ordered_groups() -> list[str]:
    groups: list[str] = []
    for page in WIKI_PAGES:
        if page.group not in groups:
            groups.append(page.group)
    return groups


def pages_for_group(group: str) -> list[WikiPage]:
    return [page for page in WIKI_PAGES if page.group == group]


if __name__ == "__main__":
    main()
