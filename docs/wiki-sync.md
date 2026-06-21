# Wiki Sync Guide

本文档说明 GitHub Wiki 的生成与自动同步机制。

## Source Of Truth

Wiki 不手工维护。源文档仍在仓库中：

- `README.md`
- `docs/*.md`
- `scripts/build-wiki.py`

CI 会把这些文档转换成 GitHub Wiki 页面并推送到：

```text
https://github.com/lyl224459/CloudSim-Benchmark/wiki
```

对应 Git 仓库：

```text
https://github.com/lyl224459/CloudSim-Benchmark.wiki.git
```

## Generated Pages

生成输出位于：

```text
build/wiki/
```

固定生成：

- `Home.md`
- `_Sidebar.md`
- README 对应的 `Project-Overview.md`
- 每个重要 `docs/*.md` 对应的 wiki 页面

页面底部会写入来源文件，提醒维护者不要直接编辑 wiki。

## Local Build

本地预览生成内容：

```powershell
python scripts/build-wiki.py
```

输出：

```text
build/wiki/Home.md
build/wiki/_Sidebar.md
build/wiki/*.md
```

`build/wiki` 是生成物，不提交。

## CI Workflow

Workflow：

```text
.github/workflows/wiki-sync.yml
```

触发：

- push 到 `main`，且 README/docs/wiki 脚本/workflow 有变化；
- 手动 `workflow_dispatch`。

CI 步骤：

1. checkout main 仓库；
2. 运行 `python scripts/build-wiki.py`；
3. clone GitHub Wiki 仓库；
4. 用 `build/wiki/` 覆盖 wiki repo；
5. 如果内容有变化，提交并推送；
6. 如果无变化，直接退出成功。

## Permissions

Workflow 使用：

```yaml
permissions:
  contents: write
```

它通过 `GITHUB_TOKEN` 推送 wiki 仓库。仓库必须启用 GitHub Wiki；如果 wiki 被禁用或 token 无法写入 wiki repo，同步 job 会失败。

## Link Rewriting

`scripts/build-wiki.py` 会重写常见相对链接：

- `docs/configuration.md` -> `Configuration-Guide`
- `configuration.md` -> `Configuration-Guide`
- `README.md` -> `Project-Overview`

外部链接、邮件链接和当前页 anchor 不会改写。

## Adding A Wiki Page

新增 wiki 页面时：

1. 在 `docs/` 中创建源 Markdown。
2. 将文档加入 README 的 Documentation 表。
3. 将文档加入 `scripts/build-wiki.py` 的 `WIKI_PAGES`。
4. 本地运行 `python scripts/build-wiki.py`。
5. 运行文档漂移测试。

推荐验证：

```powershell
python scripts/build-wiki.py
.\gradlew.bat test --tests "config.DocumentationDriftTest" --no-daemon --stacktrace
.\gradlew.bat verifyGitHubActionsPolicy --no-daemon --stacktrace
git diff --check
```

## Manual Recovery

如果 wiki 被手工编辑导致漂移，下一次 workflow 会用仓库源文档覆盖 wiki。需要保留的手工内容应先迁回 `docs/`。

如果 wiki repo 为空或首次启用，workflow 会初始化本地 repo 并推送 `master` 分支。

## Troubleshooting

| Symptom | Likely Cause | Action |
| :--- | :--- | :--- |
| `git clone ...wiki.git` fails | Wiki disabled or first-time empty wiki | 启用 GitHub Wiki；首次空仓库由 workflow 初始化。 |
| push fails with 403 | `GITHUB_TOKEN` 权限不足 | 检查 workflow `contents: write` 和仓库 Actions 权限。 |
| links point to repo docs | Missing mapping in `WIKI_PAGES` | 把源文档加入 `scripts/build-wiki.py`。 |
| wiki page edits disappear | Wiki is generated | 修改仓库源文档，不直接改 wiki。 |
