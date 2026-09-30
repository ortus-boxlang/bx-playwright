# bx-playwright: Research and Consolidated Plan

Status: draft v2 (engine, bundling, TestBox location and scope decisions confirmed). No code yet. API shapes below are proposals to agree on before implementation.

## 1. Goal

One BoxLang module that replaces both `cbPlaywright` and `commandbox-cbplaywright`:

- Ships the Playwright Java bindings and manages the driver and browsers itself.
- Provides a CLI (install browsers, codegen, show-trace, etc.).
- Offers a fluent BoxLang DSL usable anywhere: tests, scheduled tasks, scraping, PDF/screenshot generation.
- Exposes a testing SPI so TestBox core can build its Playwright integration (base specs, matchers, failure artifacts).

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

## 3. Decisions

Confirmed:

1. **BoxLang native only.** No Adobe/Lucee. Uses the full BoxLang module surface: BIFs, components, interceptors, module settings, CLI `main()`, closures/lambdas bridged to Java functional interfaces.
2. **New project.** cbPlaywright and commandbox-cbplaywright are inspiration only. No compat layer, no migration shims.
3. **Bundle all jars, including `driver-bundle` (~204 MB)**, so the module works offline out of the box. `PLAYWRIGHT_NODEJS_PATH` stays as an optional override (system Node).
4. **TestBox adapter lives in TestBox core.** bx-playwright provides the engine and a stable testing SPI (lifecycle, artifact hooks, assertion API). TestBox builds its specs, matchers and reporting on top (see 7).

Proposed (not yet confirmed):

5. **One module, `bx-playwright`**, registered as `playwright`. CLI and runtime DSL in the same module.
6. **Single version source**: the Playwright version comes from the bundled jars at build time (Gradle). Driver and Node always match it.
7. **Persistent home** `~/.boxlang/playwright/` (overridable): `driver/` (extracted once from the bundled jars via `CLI install-driver`) and `browsers/`. The module sets `PLAYWRIGHT_DRIVER_DIR`, `PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` so nothing is extracted per launch and browsers install only via the CLI.
8. **Thread confinement built in**: the DSL never shares a Java `Playwright` across threads. A per-thread manager (and an optional pool for web/scheduler use).

## 4. Architecture

```
bx-playwright/
  src/main/bx/
    ModuleConfig.bx          settings, onLoad env wiring, main( args ) for the CLI
    bifs/                    Playwright() and utility BIFs (see 6.7)
    components/              bx:playwright, bx:page, ... (see 6.8)
    interceptors/            lifecycle events for extensions (see 6.9)
    models/
      PlaywrightService.bx   home/driver/node/browsers resolution, install, doctor
      Manager.bx             thread-confined Playwright + browser cache
      Browser.bx / Context.bx / Page.bx / Locator.bx / Request.bx   fluent wrappers
      Expect.bx              web-first assertions (wraps PlaywrightAssertions)
      OptionsMapper.bx       struct -> Java *Options via reflection
      Devices.bx             device descriptors (JSON shipped with the module)
      cli/                   one class per CLI command
      testing/               framework-agnostic testing SPI consumed by TestBox (see 7)
  src/main/java/             only if needed (e.g. image diff, event bridging)
  libs/                      playwright, driver, driver-bundle jars (+ gson, etc.)
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

### 6.7 BIFs

Keep the global surface small; everything else hangs off the returned objects.

| BIF | Returns / does |
|---|---|
| `playwright( [options] )` | Thread-confined manager (entry point for everything) |
| `playwrightScreenshot( url, path, [options] )` | One-shot screenshot, cleans up |
| `playwrightPDF( url, path, [options] )` | One-shot PDF (Chromium) |
| `playwrightContent( url, [options] )` | Rendered HTML after JS (scraping) |
| `playwrightDevices( [name] )` | Device descriptor struct(s) |

### 6.8 Components

For templates, scripts and scheduled tasks, a block style that auto-manages cleanup:

```js
bx:playwright browser="chromium" headless=true variable="pw" {
    bx:page url="https://site.com/report" device="iPhone 15" variable="page" {
        page.click( "Export" )
        bx:playwrightScreenshot path="report.png" fullPage=true;
    }
}
```

Candidates: `bx:playwright`, `bx:browserContext`, `bx:page`, `bx:playwrightTrace`, `bx:playwrightScreenshot`, `bx:playwrightPDF`. Scope to agree on in phase 2.

### 6.9 Interceptors (extension points)

Announced events so other modules (and TestBox) can hook in without subclassing:
`onPlaywrightCreate`, `onBrowserLaunch`, `onContextCreate`, `onPageCreate`, `onPageClose`, `onPlaywrightAssertionFailure`, `onPlaywrightArtifact` (screenshot/trace/video written). TestBox uses these to attach artifacts and apply policies.

## 7. TestBox Integration (lives in TestBox core)

Split of responsibilities:

| bx-playwright provides (testing SPI) | TestBox core builds |
|---|---|
| Manager/context/page lifecycle API with scopes (run, bundle, spec) | Base specs or annotations that wire those scopes into spec lifecycle |
| Artifact API: start/stop trace, video, screenshot, console log, HTML dump, with a policy object (`off, on, only-on-failure, retain-on-failure`) | Applying policies on spec pass/fail, attaching artifacts to results and reporters |
| Web-first assertion API throwing `AssertionFailedError` | `expect( page ).toHaveTitle()`, `expect( locator ).toBeVisible()` matchers |
| Interceptor events (6.9) | Listeners, reporting, TestBox RUN UI integration |
| Device registry, storage-state sessions, webServer helper | Browser matrix, retries, sharding, config (`this.playwright = {}`, env vars) |

Proposed TestBox-side features (for the TestBox team to own):

- Tiered base specs (pick how much lifecycle you want): `PlaywrightSpec` (manager), `BrowserSpec` (shared browser), `ContextSpec` (fresh context per spec), `PageSpec` (fresh page per spec, exposes `page` and `visit()`).
- Artifacts written to `tests/results/playwright/<bundle>/<spec>/`, linked from reporters.
- Browser matrix, retries with trace on first retry, optional `webServer` to boot the app before specs.
- A generic "attach artifact to spec result" API in TestBox (currently not in TestBox docs), useful beyond Playwright.

Interface contract between the two repos must be agreed early (phase 0) since they release separately. bx-playwright ships a minimal internal harness for its own tests only.

## 8. Phased Roadmap and Tasks

### Phase 0: Foundations
- [ ] Run `SetupTemplate` (slug `bx-playwright`, mapping `playwright`), clean example BIFs/components.
- [ ] Gradle: add `playwright`, `driver`, `driver-bundle` deps into `libs/`, stamp version into `box.json` and `ModuleConfig`. Check module zip size and ForgeBox limits.
- [ ] Draft the testing SPI contract with the TestBox team.
- [ ] Spike: load jars in the module classloader, create `Playwright` with `PLAYWRIGHT_DRIVER_DIR` and system Node. Confirm thread confinement behavior under BoxLang.
- [ ] Spike: find a stable way to ship device descriptors (extract from driver bundle at build time).

### Phase 1: Install and CLI
- [ ] `PlaywrightService`: home resolution, one-time driver extraction from bundled jars (`install-driver`), env wiring, `PLAYWRIGHT_NODEJS_PATH` override.
- [ ] `ModuleConfig.main()` dispatcher and commands: `install`, `install-deps`, `uninstall`, `doctor`, `version`, `run`.
- [ ] Commands: `codegen`, `open`, `show-trace`, `screenshot`, `pdf`, `mcp`, `devices`.
- [ ] GitHub Action example for CI (install with deps, cache browsers).

### Phase 2: Core DSL
- [ ] `OptionsMapper` with tests for every options class used.
- [ ] `Manager` (thread-confined, auto cleanup), `Browser`, `Context`, `Page`, `Locator` wrappers.
- [ ] Smart selector resolver.
- [ ] Assertions (inline + `pwExpect`), web-first, configurable timeout.
- [ ] Network (`intercept`, events), `request()` API testing, storage state `session()`, tracing, video, screenshots, PDF, clock.

### Phase 3: BIFs, components, interceptors, testing SPI
- [ ] BIFs (6.7) and components (6.8).
- [ ] Interceptor events (6.9).
- [ ] Testing SPI: lifecycle scopes, artifact API and policies, device registry, webServer helper.
- [ ] Support the TestBox team building the adapter in TestBox core (tracked in the TestBox repo).

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

## 9. Risks and Unknowns

- Codegen emits Java/JS/Python/.NET only. A BoxLang target needs translation of Java output or a custom recorder. Unverified effort.
- Device descriptors are not a public Java API; extraction path from the driver bundle needs a spike.
- Visual diff needs a pixel comparison implementation (Java has only `screenshot()`).
- Thread confinement vs BoxLang web requests and async: needs design validation in the Phase 0 spike.
- Bundling `driver-bundle` makes the module ~204 MB (all platforms). Confirm ForgeBox/download limits; a later option is per-platform builds.
- TestBox adapter lives in another repo with its own release cycle: the SPI must be versioned and stable early.
- TestBox retries and artifact attachment to results: confirm what TestBox 7 exposes.

## 10. Open Questions

1. Module registration name: `playwright` (BIF `playwright()`) OK?
2. Minimum BoxLang version and JDK (template says BoxLang 1.13.0, JDK 21).
3. Keep a CommandBox command namespace (`box playwright ...`) or rely on `box boxlang cli module:playwright`?
4. Assertion style: Dusk-style `assertSee()`, expect-style, or both (plan: both)?
5. Components: which ones are worth shipping in v1 (6.8)?
