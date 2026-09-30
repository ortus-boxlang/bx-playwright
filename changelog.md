# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

### Added

* Homepage for [bxplaywright.boxlang.io](https://bxplaywright.boxlang.io) in the style of bxsites.io: hero, features, AI agents, distributions, ecosystem and professional services, plus a social card rendered by bx-playwright
* Documentation site published from `docs/` to GitHub Pages at [bxplaywright.boxlang.io](https://bxplaywright.boxlang.io) by the `docs.yml` workflow
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

### Fixed

* A relative `artifacts.directory` resolves against the current directory instead of the module folder
* `click( "text" )` clicks the matching button or link even when another element with the same text (like a heading) comes first
* `assertSee()` and `assertDontSee()` only count rendered text: text in hidden elements is not seen
* Release builds ship the `META-INF/services` registration, so `bx:playwrightRender` is found in installed modules (the build now fails if it is missing)
* `assertVisible()`, `assertMissing()`, `isVisible()`, `waitFor()` and `waitForText()` judge every match: a hidden element with the same text or selector no longer hides a visible one, and `assertMissing()` no longer hits strict mode errors with several hidden matches
* `count( text )` and `expect( text ).toHaveCount()` count every element with the text instead of at most one
* `assertCount()` resolves `@alias` selectors from page objects and components
* `assertPathIs()` is case sensitive and works for URLs without a host, such as `file://`
* `assertNoSmoke()` reports JavaScript errors on every visited URL, also after a URL that had errors
* `freezeTime()` accepts BoxLang dates
* `filter( { has, hasNot } )` and screenshot `mask` options accept Locators: no more duplication errors, and locators built from the page (now scoped with `:scope`) match inside `has`
* `assertScreenshotMatches()` resolves a relative `directory` against the working directory instead of the installed module
* A nested `soft()` adds its failures to the outer `soft()` instead of losing them
* `Locator.texts()` returns the text of visible elements only
* `Locator.nth( 0 )` throws `Playwright.InvalidOption` instead of returning the last element
* `upload( files )` on a locator uploads to the locator itself, like `fill( value )` (codegen output such as `page.getByLabel( "Resume" ).upload( "cv.pdf" )` works)
* Smart selectors with spaces inside quotes, brackets or parentheses (`input[placeholder="Your email"]`, `button:has-text("Sign in")`) and Playwright chains (`div >> text=Foo`, `nav >> nth=0`) are used as selectors instead of visible text
* `click( "text" )` waits briefly for a matching button or link before falling back to visible text, so a button rendered a moment later still wins over a same text heading
* `click( "Save" )` prefers the button or link named exactly "Save" over one named "Save draft"
* `fill( "Email" )` follows its documented priority (exact label, label, placeholder, name) instead of document order, so "Backup email" or a placeholder no longer wins over the "Email" label
* Options set to `null` reach Playwright (e.g. `viewport : null` disables the fixed viewport); nulls are only skipped for primitive options
* Concurrent driver extraction is safe: installs are serialized per home (JVM and file lock), the shared jar file system is no longer closed under other threads, and an installed driver is never deleted by a late thread
* Node.js installs extract into a private staging folder, check that the executable runs and then move it into place, so a failed or interrupted install never counts as installed
* Concurrent Node.js installs use their own download file and staging folder and are serialized by a file lock, downloading only once
* Unsupported platforms throw the typed `Playwright.UnsupportedPlatform` error, and Windows ARM64 works when `nodePath` is set (using the Windows x64 layout, like Playwright Java)
* Only x64 (`amd64`, `x86_64`) and arm64 (`aarch64`, `arm64`) CPUs are accepted; other architectures (x86, arm, ppc64le, s390x, riscv64) no longer silently map to x64
* `bx:playwrightRender` no longer overrides `options={ type : "png" }` with a PDF, and an invalid `viewport` such as `1200xabc` throws `Playwright.InvalidOption`
* The Node.js version probe cannot hang: a node executable that never exits is killed after the timeout
* CLI passthrough commands no longer set `PW_LANG_NAME=java`, so Playwright's help and hints stop suggesting `mvn exec:java` commands
* An explicit `nodePath` that does not exist or is not executable is no longer reported as available; using it fails with `Playwright.NotInstalled` naming the bad path
* The `default` profile is empty, so the `browser`, `headless` and `viewport` module settings in `boxlang.json` are no longer ignored
* `playwright( struct, struct )` merges the second struct over the first instead of dropping it
* Devices and viewports follow "last wins": a viewport from a later profile or from options replaces the device screen (keeping its user agent, scale and touch), and a later device replaces earlier viewports
* Artifact folders use the `yyyyMMdd-HHmmss` date mask (minutes were used in place of the month)
* `render()` and `bx:playwrightRender` resolve relative links and assets against `baseURL` (a `<base href>` is added to the markup)
* `render()` accepts a `WIDTHxHEIGHT` viewport string and throws `Playwright.InvalidOption` for other non-struct values
* `request().close()` stops the Playwright driver that `request()` started, so API clients no longer leak a driver process
* `visit()` closes the new context (and the manager it started) when the navigation fails, then rethrows the error
* `session()` setup never loads the session configured on the manager: creating it no longer fails with "does not exist yet" and `refresh` starts from a clean page
* The AI browser accepts refs of elements inside iframes, such as `f1e2`
* `BrowserContext.close()` always closes and forgets the context even when collecting screenshots or traces fails, and `browse()` no longer hides the callback error behind a close error
* Session names map to distinct files (`a b` and `a-b` no longer share one); simple names such as `admin` keep their file
* A relative `snapshots.directory` resolves against the current directory instead of the module folder
* CLI `codegen --output` writes relative paths to the current directory (not the module folder) and reports the absolute path
* CLI `codegen --output file` and `-o file` (space separated) work: flags that take a value accept the next argument, and it is no longer forwarded to Playwright as a URL
* CLI `codegen` produces BoxLang that runs: `#` and quotes in strings are escaped, Java escapes resolved, `Pattern.compile()` becomes `page.regex()` with flags, enum constants become strings, `locator().contentFrame()` becomes `frame()`, popups, downloads and dialogs become callbacks, and multi-line aria snapshots are kept
* CLI `--json` prints only valid JSON for `install`, `install-node` and `codegen` (progress and Playwright output go to standard error), and passthrough verbs no longer forward `--json` to Playwright
* CLI `help nope --json` and `nope --json` print a `Playwright.InvalidOption` JSON error and exit with 1
* CLI `doctor` fails (and `install` stops with an error) when the Node.js runtime cannot run, such as an explicit `nodePath` that does not exist, instead of reporting it as ok
* CLI `version` reports the Node.js runtime actually used and its source, or `none`
* CLI `mcp` uses the configured browser (`--browser=chromium` by default) instead of the branded Chrome that `install` does not install
* CLI `--x="abc` keeps its value: quotes are only stripped when the value starts and ends with the same quote
* CLI built-in verbs reject unknown options (such as `install --with-dep`) with `Playwright.InvalidOption` listing the valid ones, instead of ignoring them
* Release workflow: a build of a snapshot version or from the development branch (including a manual run) is never tagged or released
