---
title: CLI
order: 32
summary: The bxplaywright command line.
tags: [reference, cli]
---

# CLI

`bxplaywright` installs browsers, checks your setup and wraps Playwright's tools. It is installed with the module, together with its bash completions (zsh, fish and PowerShell completions are one command away, see [Tab completions](#tab-completions)). `boxlang module:bxplaywright <verb>` runs the same commands.

```bash
bxplaywright install chromium --with-deps   # browsers and Linux libraries
bxplaywright doctor                         # what is installed, with fix hints
bxplaywright profiles mobile                # one profile, fully resolved
bxplaywright codegen http://localhost:8080 -o login.bxs
bxplaywright show-trace trace.zip
```

## Options

Add `--json` for machine readable output: only the JSON document goes to standard output (progress and Playwright output go to standard error), and errors are printed as `{"error": {"type": "...", "message": "...", "detail": "..."}}`.

Options are `--flag`, `--flag=value` or `--flag value`, and `--no-flag` for false. Built-in verbs reject options they do not know (so a typo such as `--with-dep` fails and lists the valid options); verbs that forward to Playwright (`codegen`, `open`, `screenshot`, `pdf`, `show-trace`, `mcp`, `install-deps`, `uninstall`, `run`) pass unknown options through.

## Verbs

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
| `open [url] [--device] [--browser]` | Open a headed browser |
| `screenshot <url> <file> [--full-page]`, `pdf <url> <file> [--paper-format=A4]` | Quick captures |
| `show-trace [trace.zip]` | Trace viewer |
| `mcp` | Playwright MCP server (uses the configured browser, `--browser=chromium` by default, unless you pass `--browser`) |
| `run <args...>` | Any Playwright CLI command |
| `completions` | Print the tab completion script for `bash` (default), `zsh`, `fish` or `powershell` |
| `help [verb] [--json]` | Help |

Exit codes: `0` on success, `1` on failure. Errors print their type and a `Fix:` line.

Download problems (proxy, timeouts, using an installed Chrome) are covered in [Getting Started](getting-started.md#browser-download-problems).

## Tab completions

`install-bx-module bx-playwright` installs the bash completions: open a new shell and press Tab after `bxplaywright`. They complete the verbs, their flags, browser names, profile names and file paths. The other shells print their script with `bxplaywright completions <shell>`:

| Shell | Load the completions |
| --- | --- |
| bash | Installed with the module. Without the installer: `source <(bxplaywright completions bash)` |
| zsh | `source <(bxplaywright completions zsh)` in `~/.zshrc`, or save it as `_bxplaywright` in a folder of `$fpath`: `bxplaywright completions zsh > "${fpath[1]}/_bxplaywright"` |
| fish | `bxplaywright completions fish > ~/.config/fish/completions/bxplaywright.fish` |
| PowerShell | `bxplaywright completions powershell \| Out-String \| Invoke-Expression`, and add the same line to your `$PROFILE` |

The module also ships the four scripts in its `completions/` folder. They are generated from the verb list, so they always match the installed version.

