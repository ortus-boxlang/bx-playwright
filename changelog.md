# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

### Fixed

* `click( "text" )` clicks the matching button or link even when another element with the same text (like a heading) comes first
* `assertSee()` and `assertDontSee()` only count rendered text: text in hidden elements is not seen

### Added

* `playwright()` BIF: fluent browser automation with smart selectors, chainable actions, web-first assertions (inline and `expect()` style), network mocking, events, popups, downloads, screenshots, PDF, rendered content and accessibility snapshots
* `browse()` with automatic cleanup and multi-user pages, one-shot `screenshot()`, `pdf()`, `content()` and `render()`
* `request()` for API testing
* `bx:playwrightRender` component: render HTML to PDF, PNG, JPEG or WebP with Chromium
* Built-in profiles (browsers, screens, devices, appearance, modes), user profiles with `extends`, `BX_PLAYWRIGHT_*` environment overrides
* Artifact policies for screenshots, traces and videos (`off`, `on`, `only-on-failure`, `retain-on-failure`)
* `bxPlaywright` CLI with bash completions and `--json` output: install, install-node, install-deps, uninstall, doctor, version, devices, profiles, clean, codegen, open, screenshot, pdf, show-trace, mcp, run, completions, help
* Two distributions: `bx-playwright` (downloads Node.js) and `bx-playwright-full` (bundles Node.js)
* Interception points: onPlaywrightCreate, onBrowserLaunch, onContextCreate, onPageCreate, onPageClose, onPlaywrightAssertionFailure, onPlaywrightArtifact
* Visual regression: `assertScreenshotMatches()` with baselines, pixel diff and diff images (`snapshots` setting, `BX_PLAYWRIGHT_UPDATE_SNAPSHOTS`)
* Quality checks: `assertNoConsoleErrors()`, `assertNoSmoke()`, axe-core `accessibility()` and `assertNoAccessibilityIssues()`
* Page objects, page components and `macro()` extensions
* Soft assertions with `soft()`
* Emulation with `emulate()`, `device()`, `clock()` and `freezeTime()`
* `session( name, setup )` caches logged in storage state
* `codegen` translates recorded actions into the bx-playwright DSL
* AI support: `snapshot()` with element refs, `help()` introspection, `aiTools()` for bx-ai
* Runnable `examples/` executed in CI, bxSites documentation in `docs/`
