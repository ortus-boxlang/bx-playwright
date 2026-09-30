---
title: CLI
order: 22
summary: The bxPlaywright command line.
tags: [reference, cli]
---

# CLI

`bxPlaywright <verb> [options]` (or `boxlang module:playwright <verb>`). Bash completions are installed with the module. Add `--json` for machine readable output.

| Verb | Does |
|---|---|
| `install [browsers...] [--with-deps] [--only-shell] [--force] [--skip-node]` | Driver, Node.js (small distribution), then browsers (default chromium, `all` for every browser) |
| `install-node [--force]` | Only Node.js |
| `install-deps [browsers...]` | Operating system packages (Linux) |
| `uninstall [--all]` | Remove browsers |
| `doctor` | Check versions, paths, Node.js and browsers with fix hints |
| `version` | Module, Playwright and Node.js versions |
| `devices` | Device descriptors |
| `profiles [name]` | List profiles or show one resolved |
| `clean` | Remove the extracted driver and Node.js (keeps browsers) |
| `codegen [url] [--output=file.bxs] [--target=boxlang]` | Record actions as BoxLang code |
| `open [url]` | Open a headed browser |
| `screenshot <url> <file>`, `pdf <url> <file>` | Quick captures |
| `show-trace [trace.zip]` | Trace viewer |
| `mcp` | Playwright MCP server |
| `run <args...>` | Any Playwright CLI command |
| `completions` | Print the bash completion script |
| `help [verb] [--json]` | Help |

Exit codes: `0` on success, `1` on failure. Errors print their type and a `Fix:` line.
