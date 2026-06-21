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
    WikiPage("README.md", "项目概览", "Project-Overview.md", "快速入口"),
    WikiPage("docs/getting-started.md", "快速开始", "Getting-Started.md", "快速入口"),
    WikiPage("docs/cli.md", "CLI 命令参考", "CLI-Reference.md", "用户指南"),
    WikiPage("docs/configuration.md", "配置指南", "Configuration-Guide.md", "用户指南"),
    WikiPage("docs/config-reference.md", "配置字段参考", "Configuration-Reference.md", "用户指南"),
    WikiPage("docs/experiment-cookbook.md", "实验场景手册", "Experiment-Cookbook.md", "用户指南"),
    WikiPage("docs/algorithms.md", "调度算法指南", "Algorithms-Guide.md", "调度与指标"),
    WikiPage("docs/realtime-scheduling.md", "实时调度", "Realtime-Scheduling.md", "调度与指标"),
    WikiPage("docs/realtime-metrics.md", "实时指标", "Realtime-Metrics.md", "调度与指标"),
    WikiPage("docs/google-trace.md", "Google Trace 数据", "Google-Trace.md", "数据与结果"),
    WikiPage("docs/results-and-analysis.md", "结果与分析", "Results-And-Analysis.md", "数据与结果"),
    WikiPage("docs/performance.md", "性能指南", "Performance-Guide.md", "数据与结果"),
    WikiPage("docs/container.md", "容器指南", "Container-Guide.md", "运维与交付"),
    WikiPage("docs/architecture.md", "系统架构", "Architecture.md", "开发文档"),
    WikiPage("docs/api.md", "API 与扩展指南", "API-And-Extension-Guide.md", "开发文档"),
    WikiPage("docs/development.md", "开发指南", "Development-Guide.md", "开发文档"),
    WikiPage("docs/testing.md", "测试指南", "Testing-Guide.md", "开发文档"),
    WikiPage("docs/build-logic.md", "构建逻辑", "Build-Logic.md", "开发文档"),
    WikiPage("docs/ci-workflows.md", "CI 工作流", "CI-Workflows.md", "运维与交付"),
    WikiPage("docs/supply-chain.md", "供应链安全", "Supply-Chain.md", "运维与交付"),
    WikiPage("docs/release-package.md", "发布包", "Release-Package.md", "运维与交付"),
    WikiPage("docs/release-readiness.md", "发布就绪检查", "Release-Readiness.md", "运维与交付"),
    WikiPage("docs/troubleshooting.md", "故障排查", "Troubleshooting.md", "快速入口"),
    WikiPage("docs/glossary.md", "术语表", "Glossary.md", "快速入口"),
    WikiPage("docs/wiki-sync.md", "Wiki 同步", "Wiki-Sync.md", "运维与交付"),
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
        "本 Wiki 由仓库中的 `README.md` 和 `docs/` 目录自动生成。",
        "请在主仓库中修改源文档，不要直接编辑 Wiki 页面。",
        "",
        "## 快速入口",
        "",
        "- [项目概览](Project-Overview)",
        "- [快速开始](Getting-Started)",
        "- [故障排查](Troubleshooting)",
        "- [术语表](Glossary)",
        "",
        "## 完整目录",
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
