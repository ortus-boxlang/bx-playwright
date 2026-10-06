---
title: CLI
order: 22
summary: The bxPlaywright command line.
tags: [reference, cli]
---

# CLI

`bxPlaywright <verb> [options]` (or `boxlang module:playwright <verb>`). Bash completions are installed with the module. Add `--json` for machine readable output: only the JSON document goes to standard output (progress and Playwright output go to standard error), and errors are printed as `{"error": {"type": "...", "message": "...", "detail": "..."}}`.

Options are `--flag`, `--flag=value` or `--flag value`, and `--no-flag` for false. Built-in verbs reject options they do not know (so a typo such as `--with-dep` fails and lists the valid options); verbs that forward to Playwright (`codegen`, `open`, `screenshot`, `pdf`, `show-trace`, `mcp`, `install-deps`, `uninstall`, `run`) pass unknown options through.

| Verb | Does |
|---|---|
| `install [browsers...] [--with-deps] [--only-shell] [--force] [--skip-node]` | Driver, Node.js (small distribution), then browsers (default chromium, `all` for every browser) |
| `install-node [--force]` | Only Node.js |
| `install-deps [browsers...]` | Operating system packages (Linux) |
| `uninstall [--all]` | Remove browsers |
| `doctor` | Check versions, paths, Node.js and browsers with fix hints (a `nodePath` that does not run fails) |
| `version` | Module, Playwright and the Node.js actually used (or `none`) |
| `devices` | Device descriptors |
| `profiles [name]` | List profiles or show one resolved |
| `clean` | Remove the extracted driver and Node.js (keeps browsers) |
| `codegen [url] [--output=file.bxs\|-o file.bxs] [--target=boxlang]` | Record actions as BoxLang code (relative outputs are relative to the current directory; untranslated lines are kept as `// TODO translate:` comments) |
| `open [url]` | Open a headed browser |
| `screenshot <url> <file>`, `pdf <url> <file>` | Quick captures |
| `show-trace [trace.zip]` | Trace viewer |
| `mcp` | Playwright MCP server (uses the configured browser, `--browser=chromium` by default, unless you pass `--browser`) |
| `run <args...>` | Any Playwright CLI command |
| `completions` | Print the bash completion script |
| `help [verb] [--json]` | Help |

Exit codes: `0` on success, `1` on failure. Errors print their type and a `Fix:` line.
