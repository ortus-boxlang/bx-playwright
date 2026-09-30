# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

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
