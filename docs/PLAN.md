# bx-playwright: Research and Consolidated Plan

Status: draft for review. No code yet. API shapes below are proposals to agree on before implementation.

## 1. Goal

One BoxLang module that replaces both `cbPlaywright` and `commandbox-cbplaywright`:

- Ships the Playwright Java bindings and manages the driver and browsers itself.
- Provides a CLI (install browsers, codegen, show-trace, etc.).
- Offers a fluent BoxLang DSL usable anywhere: tests, scheduled tasks, scraping, PDF/screenshot generation.
- Offers a TestBox integration (base specs, matchers, failure artifacts).

## 2. Research Summary

### 2.1 Playwright Java (latest 1.63.0, Sept 2026)

- Maven artifacts (`com.microsoft.playwright`):
  - `playwright` (~0.7 MB): the Java API.
  - `driver` (~3.2 MB): the playwright-core JS bundle (`cli.js`).
  - `driver-bundle` (~204 MB): Node binaries for every platform. Optional if `PLAYWRIGHT_NODEJS_PATH` points to a system Node.
- Runtime: Java spawns `node cli.js run-driver` and talks JSON over a pipe. The driver is extracted to a temp dir on every `Playwright.create()` unless `PLAYWRIGHT_DRIVER_DIR` (or `-Dplaywright.cli.dir`) points to a pre-extracted one. `CLI install-driver <dir>` extracts it once.
- Browsers: cached in `PLAYWRIGHT_BROWSERS_PATH` (default `~/.cache/ms-playwright`). Auto-installed on create unless `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` is set. Since 1.57 Chromium is Chrome for Testing.
- CLI main class `com.microsoft.playwright.CLI`: `install [--with-deps]`, `install-deps`, `uninstall`, `codegen`, `open`, `screenshot`, `pdf`, `show-trace`, plus (bundled, undocumented for Java) `trace ...` and `mcp`.
- Threading: not thread safe. One `Playwright` instance per thread; all calls on its creating thread. `Thread.sleep` starves events.
- API: synchronous, options via nested builder classes (`Page.NavigateOptions().setTimeout()`), enums in `com.microsoft.playwright.options`.
- Assertions: `PlaywrightAssertions.assertThat` for Page, Locator, APIResponse. Web-first (retry until 5s default).
- Missing in Java vs Node Playwright Test: fixtures, projects, public devices registry, retries, sharding, HTML reporter, trace/video/screenshot policies, `toHaveScreenshot` visual diffs, soft assertions, `expect.poll`, webServer, global auth setup.
- Recent additions worth exposing: Clock API, ariaSnapshot, WebSocket routing, localStorage/sessionStorage API, Screencast, tracing HAR, `Locator.visible()`, WebAuthn credentials, MCP server.

### 2.2 cbPlaywright (v1.52.1)

- A single include (`PlaywrightMixins.cfm`) mixed into `PlaywrightTestCase` (TestBox) and `ColdBoxPlaywrightTestCase`.
- Consumers must add jars to `this.javaSettings` and a `/cbPlaywright` mapping by hand.
- Depends on commandbox-cbplaywright for the driver. Strict version match check between `playwright.version` and the driver's `package.json`.
- API is free functions over raw Java objects: `navigate(page, path)`, `click(locator)`, `fill(locator, value)`, `getByRole(page, role, {})`, `launchBrowser()`, `screenshotPage()`, `traceContext()`, `newRecordedContextForBrowser()`, `waitForPopup()`, `storeStorageState()`, `route()` (URL builder), `regex()`.
- Pain points:
  - Most helpers hard-code empty options (click, fill, navigate, waitForLoadState).
  - `getByRole` ignores its named args, `traceContext` forces `sources=false`, uses deprecated `waitForNavigation`.
  - No web-first assertions, matchers, network routing, API testing, or context options beyond video/storageState.
  - No auto-close of browsers/contexts, no failure artifacts.
  - Adobe/Lucee hacks: error string matching for `super.beforeAll`, PageContext cloning and exclusive locks for Java callbacks.
  - Version is duplicated in four places; CI cron pinned to a stale driver.

### 2.3 commandbox-cbplaywright (v1.1.6)

- Commands: `playwright` / `playwright-cli` (raw passthrough to driver CLI) and `cbplaywright driver install [version] [directory] [--force]`.
- Downloads `driver-bundle` jar from Maven Central, extracts one platform folder, writes launcher scripts, chmod 777.
- Auto-installs a hard-coded 1.52.0 on module load.
- Pain points:
  - "latest" is unsorted and unfiltered.
  - No `mac-arm64` detection (Apple Silicon gets x64 node).
  - `directory` argument breaks template lookup; arguments are not quoted.
  - No shared config with cbPlaywright (browsers path, versions).

### 2.4 Ecosystem ideas (JVM, PHP, others)

| Source | Idea worth taking |
|---|---|
| Pest 4 browser (PHP, uses Playwright) | `visit()` chain, device/dark mode modifiers, `assertNoSmoke()`, a11y assertions, `assertScreenshotMatches()`, `debug()` |
| Laravel Dusk | Page classes with `@alias` elements, Components, `within()`, macros, `loginAs()`, multiple browsers per test, failure screenshots + console logs |
| Selenide | `$` / `$$`, `shouldHave` / `shouldBe` conditions that double as waits |
| Geb (Groovy) | Page objects with `url`, `at` checker, `content` DSL, `to:` navigation |
| Capybara | Actions by label/text (`fillIn("Email")`, `clickButton("Save")`), escape hatch to raw page |
| pytest-playwright | `--tracing/--video/--screenshot retain-on-failure` policies, fixture scopes |
| Playwright .NET | Tiered base classes: `PageTest`, `ContextTest`, `BrowserTest`, `PlaywrightTest` |
| JUnit `@UsePlaywright`, Quarkus | Options factory, annotation-driven setup, runtime (non-test) usage |
| Serenity Screenplay | Named saved sessions, console error checks, network mocking in DSL |
| Cypress / WebdriverIO | `cy.session` auth caching, custom commands at page and element level |

## 3. Decisions (proposed)

1. **BoxLang only.** No Adobe/Lucee. This removes all PageContext and error-string hacks.
2. **One module, `bx-playwright`**, registered as `playwright`. CLI and runtime DSL in the same module.
3. **Bundle `playwright` + `driver` jars in `libs/`. Do not bundle `driver-bundle` (204 MB).** Node is resolved in this order: `PLAYWRIGHT_NODEJS_PATH`, module setting, then a per-platform Node downloaded on demand by `install` (from the matching `driver-bundle` version on Maven Central, extracting only the needed platform, including `mac-arm64`).
4. **Single version source**: the Playwright version comes from the bundled jar at build time (Gradle). The driver and Node always match it. No user-facing version juggling.
5. **Persistent home** `~/.boxlang/playwright/` (overridable): `driver/`, `node/`, `browsers/`. The module sets `PLAYWRIGHT_DRIVER_DIR`, `PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` so nothing is extracted per launch.
6. **TestBox adapter lives in bx-playwright** (as the `playwright.testing` package), not in TestBox. TestBox already exposes what we need (base spec `extends`, `addMatchers`, lifecycle and `aroundEach` hooks, TestBox modules with `onSpecFailure`). Keeping it here keeps versions aligned. TestBox can later add a generic "attach artifact to result" API, which we would use.
7. **Thread confinement built in**: the DSL never shares a Java `Playwright` across threads. A per-thread manager (and an optional pool for web/scheduler use).

## 4. Architecture

```
bx-playwright/
  src/main/bx/
    ModuleConfig.bx          settings, onLoad env wiring, main( args ) for the CLI
    bifs/                    Playwright(), and a few top-level BIFs (see 6.1)
    models/
      PlaywrightService.bx   home/driver/node/browsers resolution, install, doctor
      Manager.bx             thread-confined Playwright + browser cache
      Browser.bx / Context.bx / Page.bx / Locator.bx / Request.bx   fluent wrappers
      Expect.bx              web-first assertions (wraps PlaywrightAssertions)
      OptionsMapper.bx       struct -> Java *Options via reflection
      Devices.bx             device descriptors (JSON shipped with the module)
      cli/                   one class per CLI command
    testing/
      PlaywrightSpec.bx, BrowserSpec.bx, ContextSpec.bx, PageSpec.bx
      Matchers.bx, ArtifactPolicy.bx, TestBoxModule (onSpecFailure)
  src/main/java/             only if needed (e.g. image diff, event bridging)
  libs/                      playwright + driver jars (+ gson, etc.)
```

Key internal pieces:

- **OptionsMapper**: converts `{ timeout: 5000, waitUntil: "networkidle" }` to `Page.NavigateOptions` by reflecting setters and coercing enums, paths, regex, and nested structs. This gives every Java option to users with zero hand-written glue and no hard-coded empty options.
- **Raw escape hatch**: every wrapper exposes `.$raw()` (or `getJava()`) returning the underlying Java object.
- **Events**: BoxLang closures bridged to Java `Consumer`/`Runnable` via BoxLang's native functional interface support (no Runnable proxy class needed).

## 5. CLI

Entry point is BoxLang's module CLI convention: `ModuleConfig.main( args )`, invoked as `boxlang module:playwright <command>`. CommandBox users run `box boxlang cli module:playwright <command>` (or a thin CommandBox alias later if wanted).

| Command | Purpose |
|---|---|
| `install [chromium firefox webkit msedge chrome] [--with-deps] [--only-shell]` | Ensure driver + Node, then install browsers |
| `install-deps [browsers]` | OS dependencies (Linux) |
| `uninstall [--all]` | Remove browsers |
| `doctor` | Report versions, paths, platform, Node, installed browsers, and fix hints |
| `version` | Module, Playwright, and browser versions |
| `codegen [url] [--device --viewport --load-storage --save-storage]` | Record actions (Java target for now; BoxLang target later, see 10) |
| `open [url]`, `screenshot <url> <file>`, `pdf <url> <file>` | Quick utilities |
| `show-trace [file]` | Open the trace viewer |
| `mcp [options]` | Start the Playwright MCP server (bundled in driver) |
| `devices` | List device descriptors |
| `run <args...>` | Raw passthrough to the Playwright CLI |

## 6. Runtime DSL (API shape)

### 6.1 Entry points

```js
// Manager (thread-confined, auto-closes on request/thread end or explicit close)
pw = playwright( { browser: "chromium", headless: true, baseURL: "http://localhost:8080" } )

// One-liners
page = pw.visit( "/login" )                     // launches browser + context + page lazily
pw.browse( ( page ) => { ... } )                 // closure scope, auto cleanup
pw.browse( ( alice, bob ) => { ... } )           // multi-user: isolated contexts

// Utilities outside tests
playwright().screenshot( "https://site.com", "shot.png", { fullPage: true } )
playwright().pdf( "https://site.com/invoice/1", "invoice.pdf", { format: "A4" } )
```

### 6.2 Page (fluent, every action returns the page)

```js
pw.visit( "/login" )
    .on().iPhone15().inDarkMode()               // device, colorScheme, locale, geo, timezone
    .fill( "Email", "luis@ortus.com" )            // smart selector (see 6.3)
    .fill( "@password", "secret" )
    .press( "Sign in" )
    .waitForUrl( "/dashboard" )
    .assertSee( "Welcome" )
    .assertPathIs( "/dashboard" )
    .screenshot( "dashboard.png" )
```

Actions: `visit/navigate, back, forward, reload, click, dblclick, fill, type, press, check, uncheck, select, upload, drag, hover, focus, scrollTo, keys, evaluate, script, wait, waitFor, waitForText, waitForUrl, waitForResponse, waitForDownload, waitForPopup, within, pause/debug`.

### 6.3 Selectors and locators

- Smart single string: `@name` -> `[data-test=name]` (attribute configurable), `#id`/`.class`/CSS/XPath pass through, `role=button[name=Save]`, otherwise text/label lookup (action-aware: `fill("Email")` tries label, then placeholder, then name).
- Explicit finders return a fluent Locator: `page.$( css )`, `page.$$( css )`, `byRole( "button", { name: "Save" } )`, `byText`, `byLabel`, `byPlaceholder`, `byAltText`, `byTitle`, `byTestId`, `frame( "#iframe" )`.
- Locator chaining: `.filter( { hasText: "x" } ).nth( 2 ).first().last().and().or().visible()`, then any action.
- Scopes: `page.within( "@cart", ( cart ) => cart.click( "Remove" ) )`.

### 6.4 Assertions (web-first, retrying)

Two styles on top of `PlaywrightAssertions`:

```js
// Inline, Dusk/Pest style, on Page and Locator
page.assertSee( "Welcome" ).assertDontSee( "Error" ).assertTitle( "Home" )
    .assertUrlIs( "/home" ).assertVisible( "@menu" ).assertValue( "Email", "a@b.com" )
    .assertCount( ".row", 3 ).assertChecked( "Remember" )

// Expect style, usable anywhere (throws opentest4j AssertionFailedError)
pwExpect( page.$( "h1" ) ).toHaveText( "Hi" ).not().toBeHidden()
```

Quality checks: `assertNoConsoleErrors()`, `assertNoSmoke( [ "/", "/about" ] )`, `assertNoAccessibilityIssues()` (axe-core injected), `assertAriaSnapshot( yaml )`, `assertScreenshotMatches()` (visual diff, phase 4). Soft mode: `page.soft( () => ... )` collects failures.

### 6.5 Network, API, state

```js
page.intercept( "**/api/users" ).respondJson( 200, { users: [] } )
page.intercept( "**/*.png" ).abort()
page.onRequest( ( r ) => ... ).onConsole( ( m ) => ... ).onDialog( ( d ) => d.accept() )

api = pw.request( { baseURL: "http://localhost:8080" } )
api.post( "/api/login", { json: { user: "a" } } ).assertOk().json()

pw.session( "admin", ( page ) => loginFlow( page ) )    // caches storageState, reused across specs
pw.visit( "/admin", { session: "admin" } )
page.clock().pauseAt( "2026-01-01" )
page.trace( "trace.zip", () => { ... } )
page.video( "videos/" )
```

### 6.6 Page objects, components, macros

```js
class LoginPage extends playwright.models.PageObject {
    url = "/login"
    elements = { email: "input[name=email]", submit: "button[type=submit]" }
    function isAt( page ){ return page.assertTitle( "Login" ) }
    function login( page, email, pass ){ return page.fill( "@email", email ).click( "@submit" ) }
}
pw.visit( new LoginPage() ).login( "a@b.com", "x" ).at( new DashboardPage() )

class DatePicker extends playwright.models.Component { selector = ".datepicker" ... }
page.within( new DatePicker(), ( dp ) => dp.selectDate( 2026, 1, 30 ) )

playwright.macro( "page", "loginAs", ( page, user ) => ... )     // page and locator level
```

Framework-aware waits: `page.waitForIdle()` (network idle + optional cbWire/HTMX hooks).

## 7. TestBox Integration

- Tiered base specs (pick how much lifecycle you want):
  - `PlaywrightSpec`: manager only.
  - `BrowserSpec`: shared browser per bundle.
  - `ContextSpec`: fresh context per spec.
  - `PageSpec`: fresh context + page per spec, exposed as `variables.page` and `visit()`.
- Config via module settings, `this.playwright = {}` on the spec, or env vars (`BX_PLAYWRIGHT_HEADLESS`, `BX_PLAYWRIGHT_BROWSER`, ...). CLI-friendly for CI.
- Artifact policies (pytest style): `trace`, `video`, `screenshot` each `off | on | only-on-failure | retain-on-failure`. Written to `tests/results/playwright/<bundle>/<spec>/`. Uses `onSpecFailure` / `aroundEach`; plus console log and page HTML dump on failure.
- Matchers registered via `addMatchers`: `expect( page ).toHaveTitle()`, `expect( locator ).toBeVisible()`, `toHaveText`, `toHaveURL`, `toHaveCount`, etc., mapped to web-first assertions.
- Browser matrix: `browsers: [ "chromium", "firefox", "webkit" ]` runs specs per browser (phase 3).
- Retries for flaky UI specs with trace on first retry (depends on what TestBox exposes; to confirm).
- Optional `webServer` setting: start the app (e.g. `boxlang miniserver`) and wait for a URL before specs.
- ColdBox: a `ColdBoxPageSpec` that combines `BaseTestCase` and the page lifecycle (phase 3).

## 8. Migration from cbPlaywright

- Provide `playwright.compat.PlaywrightTestCase` exposing the old free functions (`navigate`, `click`, `fill`, `getByRole`, `launchBrowser`, `traceContext`, ...) implemented on top of the new DSL, so existing specs run with a changed `extends`.
- Migration guide mapping each old helper to the fluent equivalent.
- Deprecate `commandbox-cbplaywright` and `cbPlaywright` with a pointer to bx-playwright (CFML engines keep the old modules).

## 9. Phased Roadmap and Tasks

### Phase 0: Foundations
- [ ] Run `SetupTemplate` (slug `bx-playwright`, mapping `playwright`), clean example BIFs/components.
- [ ] Gradle: add `playwright` + `driver` deps, shadow into `libs/`, stamp version into `box.json` and `ModuleConfig`.
- [ ] Spike: load jars in the module classloader, create `Playwright` with `PLAYWRIGHT_DRIVER_DIR` and system Node. Confirm thread confinement behavior under BoxLang.
- [ ] Spike: find a stable way to ship device descriptors (extract from driver bundle at build time).

### Phase 1: Install and CLI
- [ ] `PlaywrightService`: home resolution, platform detection (incl. `mac-arm64`, `linux-arm64`, `win32_x64`), Node download from `driver-bundle`, `install-driver`, env wiring.
- [ ] `ModuleConfig.main()` dispatcher and commands: `install`, `install-deps`, `uninstall`, `doctor`, `version`, `run`.
- [ ] Commands: `codegen`, `open`, `show-trace`, `screenshot`, `pdf`, `mcp`, `devices`.
- [ ] GitHub Action example for CI (install with deps, cache browsers).

### Phase 2: Core DSL
- [ ] `OptionsMapper` with tests for every options class used.
- [ ] `Manager` (thread-confined, auto cleanup), `Browser`, `Context`, `Page`, `Locator` wrappers.
- [ ] Smart selector resolver.
- [ ] Assertions (inline + `pwExpect`), web-first, configurable timeout.
- [ ] Network (`intercept`, events), `request()` API testing, storage state `session()`, tracing, video, screenshots, PDF, clock.

### Phase 3: TestBox adapter
- [ ] Base specs (4 tiers), config resolution, matchers.
- [ ] Artifact policies and failure dumps.
- [ ] Browser matrix, webServer, ColdBox spec.
- [ ] cbPlaywright compat layer + migration guide.

### Phase 4: Advanced
- [ ] Page objects, components, macros.
- [ ] Devices and emulation modifiers.
- [ ] Quality checks: console/smoke, axe accessibility, aria snapshots.
- [ ] Visual regression (baseline + pixel diff + diff image).
- [ ] Soft assertions, multi-user `browse()`.

### Phase 5: Tooling and docs
- [ ] BoxLang codegen target (post-process Java codegen output, or custom recorder; needs a spike).
- [ ] Docs book, examples repo, TestBox docs page.
- [ ] Optional: BoxLang AI / MCP integration story.

## 10. Risks and Unknowns

- Codegen emits Java/JS/Python/.NET only. A BoxLang target needs translation of Java output or a custom recorder. Unverified effort.
- Device descriptors are not a public Java API; extraction path from the driver bundle needs a spike.
- Visual diff needs a pixel comparison implementation (Java has only `screenshot()`).
- Thread confinement vs BoxLang web requests and async: needs design validation in the Phase 0 spike.
- On-demand Node download depends on Maven Central availability; allow a custom mirror URL and `PLAYWRIGHT_NODEJS_PATH`.
- TestBox retries and artifact attachment to results: confirm what TestBox 7 exposes.

## 11. Open Questions

1. BoxLang only, or must the runtime DSL also work on Adobe/Lucee? (Plan assumes BoxLang only.)
2. Module registration name: `playwright` (BIF `playwright()`) OK?
3. Bundle Node (204 MB) vs download on demand? (Plan: download on demand.)
4. TestBox adapter in this repo (recommended) or in TestBox core?
5. Minimum BoxLang version and JDK (template says BoxLang 1.13.0, JDK 21).
6. Keep a CommandBox command namespace (`box playwright ...`) or rely on `box boxlang cli module:playwright`?
7. Assertion style preference: Dusk-style `assertSee()`, expect-style, or both (plan: both)?
8. Should we ship a cbPlaywright compat layer, or clean break?
