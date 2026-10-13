- Saved sessions, artifacts, snapshots and the axe script are read and written on their real paths, never through BoxLang mappings. With `HOME=/root` and an application `/root` mapping (common in ColdBox apps tested in Docker as root), `session()` wrote the session inside the application while `browse( { session } )` read the real home, so the session never existed.
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

- First release of the bx-playwright module.
- The module is named `bxplaywright` everywhere: the CLI command `bxplaywright` (was `bxPlaywright`), `boxlang module:bxplaywright <verb>`, the `@bxplaywright` class suffix (for example `models.PageObject@bxplaywright`) and the `modules.bxplaywright.settings` key in `boxlang.json`. The `playwright()` and `playwrightEnsureBrowser()` BIFs keep their names.
- `playwrightEnsureBrowser( browser, channel )`: with a channel such as `chrome` or `msedge`, it uses the browser installed on the machine and downloads nothing.
- A failed browser install reports the last lines of the Playwright CLI output in the `Playwright.NotInstalled` error detail.
- Browser downloads keep Playwright's 30 second connection timeout (was 2 minutes), so a blocked network fails in about 2.5 minutes over Playwright's 5 attempts instead of 10.