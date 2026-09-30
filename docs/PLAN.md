# bx-playwright: Research and Consolidated Plan

Status: draft v3 (engine, bundling, TestBox location, naming, versions, CLI, assertions and components decided). No code yet. API shapes below are proposals to agree on before implementation.

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

1. **BoxLang native only.** No Adobe/Lucee. Uses the BoxLang module surface: BIFs, interceptors, components when needed, module settings, CLI `main()`, closures/lambdas bridged to Java functional interfaces.
2. **New project.** cbPlaywright and commandbox-cbplaywright are inspiration only. No compat layer, no migration shims.
3. **Bundle all jars, including `driver-bundle` (~204 MB)**, so the module works offline out of the box. `PLAYWRIGHT_NODEJS_PATH` stays as an optional override (system Node).
4. **TestBox adapter lives in TestBox core.** bx-playwright provides the engine and a stable testing SPI (lifecycle, artifact hooks, assertion API). TestBox builds its specs, matchers and reporting on top (see 7).
5. **One module, `bx-playwright`**, registered as `playwright`; main entry BIF `playwright()`.
6. **Targets BoxLang 1.17.x on JRE 21.**
7. **Own CLI, no CommandBox.** A `bxPlaywright` executable plus bash completions, using the module descriptor `boxlang.executable` / `boxlang.completions` fields (same pattern as bx-sites and bx-agents). See 5.
8. **Both assertion styles**: fluent inline (`page.assertSee()`) and expect style (`expect( locator ).toBeVisible()`).
9. **Components only where a body is needed.** None in v1 (see 6.8).

Proposed (not yet confirmed):

10. **Single version source**: the Playwright version comes from the bundled jars at build time (Gradle). Driver and Node always match it.
11. **Persistent home** `~/.boxlang/playwright/` (overridable): `driver/` (extracted once from the bundled jars via `CLI install-driver`) and `browsers/`. The module sets `PLAYWRIGHT_DRIVER_DIR`, `PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` so nothing is extracted per launch and browsers install only via the CLI.
12. **Thread confinement built in**: the DSL never shares a Java `Playwright` across threads. A per-thread manager (and an optional pool for web/scheduler use).

## 4. Architecture

```
bx-playwright/
  src/main/bx/
    ModuleConfig.bx          settings, onLoad env wiring, main( args ) for the CLI
    bifs/                    Playwright() (see 6.7)
    interceptors/            lifecycle events for extensions (see 6.9)
    models/
      PlaywrightService.bx   home/driver/node/browsers resolution, install, doctor
      Manager.bx             thread-confined Playwright + browser cache
      Browser.bx / Context.bx / Page.bx / Locator.bx / Request.bx   fluent wrappers
      Expect.bx              web-first assertions (wraps PlaywrightAssertions)
      OptionsMapper.bx       struct -> Java *Options via reflection
      Devices.bx             device descriptors (JSON shipped with the module)
      cli/                   one class per CLI verb, each with run( options )
  completions/
    bxPlaywright.bash        bash completions (generated from the verb registry)
      testing/               framework-agnostic testing SPI consumed by TestBox (see 7)
  src/main/java/             only if needed (e.g. image diff, event bridging)
  libs/                      playwright, driver, driver-bundle jars (+ gson, etc.)
```

Key internal pieces:

- **OptionsMapper**: converts `{ timeout: 5000, waitUntil: "networkidle" }` to `Page.NavigateOptions` by reflecting setters and coercing enums, paths, regex, and nested structs. This gives every Java option to users with zero hand-written glue and no hard-coded empty options.
- **Raw escape hatch**: every wrapper exposes `.$raw()` (or `getJava()`) returning the underlying Java object.
- **Events**: BoxLang closures bridged to Java `Consumer`/`Runnable` via BoxLang's native functional interface support (no Runnable proxy class needed).

## 5. CLI (`bxPlaywright`)

No CommandBox. The module ships its own executable through the module descriptor ([docs](https://boxlang.ortusbooks.com/boxlang-framework/module-development/module-descriptor#cli-executables-and-completions)):

```json
"boxlang": {
    "minimumVersion": "1.17.0",
    "moduleName": "playwright",
    "executable": "bxPlaywright",
    "completions": "completions/bxPlaywright.bash"
}
```

- `install-bx-module bx-playwright` creates `~/.boxlang/bin/bxPlaywright` (or `./boxlang_modules/.bin/bxPlaywright` with `--local`), equivalent to `boxlang module:playwright "$@"`.
- Completions are copied to `~/.boxlang/completions/` and auto-sourced by BVM shell init.
- Note from the docs: these fields are only processed by the OS installer (`install-bx-module` / BVM), not by `box install`. That matches the "no CommandBox" decision.

Structure, following bx-agents (`src/main/bx/ModuleConfig.bx`):

- `main( args )` only calls `CLIExit( dispatch( args ) )`, so exit codes reach the OS (a module `main()` return value is not the process exit code).
- `dispatch( args )` is testable in-process: a verb registry (`verb -> models.cli.X`), descriptions, per-verb value flags, `--help` anywhere, `--version`, unknown verb handling.
- Each verb is a class in `models/cli/` with `run( struct options )` returning `{ exitCode, message }`.
- The completions script is generated at build time from the same verb registry and flags, so it never drifts. It also completes browser names (`chromium firefox webkit msedge chrome`) and device names.

Verbs:

| Verb | Purpose |
|---|---|
| `install [browsers...] [--with-deps] [--only-shell] [--force]` | Extract the driver (first run) and install browsers |
| `install-deps [browsers...]` | OS dependencies (Linux) |
| `uninstall [--all]` | Remove browsers |
| `doctor [--json]` | Versions, paths, platform, Node, installed browsers, fix hints |
| `version` / `--version` | Module, Playwright and browser versions |
| `codegen [url] [--device --viewport --load-storage --save-storage]` | Record actions (Java target for now; BoxLang target later, see 9) |
| `open [url]` | Open a headed browser |
| `screenshot <url> <file> [--full-page --device]` | One-shot screenshot |
| `pdf <url> <file> [--format]` | One-shot PDF (Chromium) |
| `show-trace [file]` | Trace viewer |
| `mcp [options]` | Start the Playwright MCP server (bundled in driver) |
| `devices [--json]` | List device descriptors |
| `clean` | Remove extracted driver/cache in the playwright home |
| `run <args...>` | Raw passthrough to the Playwright CLI |
| `help` | Usage |

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

One BIF: `playwright()`. Everything else hangs off the returned manager, which keeps the global namespace clean and makes the API discoverable.

```js
playwright()                                   // defaults from module settings
playwright( "firefox" )                        // shorthand: browser name
playwright( { browser: "webkit", headless: false, baseURL: "..." } )

// One-shot helpers as methods (no extra BIFs)
playwright().screenshot( url, path, { fullPage: true } )
playwright().pdf( url, path, { format: "A4" } )
playwright().content( url )                     // rendered HTML after JS
playwright().devices()                          // device descriptors

// Scoped usage with auto cleanup (covers what a body component would do)
playwright().browse( ( page ) => page.visit( "/" ).assertSee( "Hi" ) )
```

Other entry ideas to decide on:

- `playwright().visit( url )` as the fastest path to a page (lazy launch of browser/context/page).
- Named profiles in module settings: `playwright( "mobile" )` resolves a profile (browser, device, baseURL, locale) when the string is not a browser name.
- `playwright().request()` for API testing without a browser.
- `playwright().connect( wsEndpoint )` / `connectOverCDP()` for remote browsers or grids.

### 6.8 Components

Rule: only when wrapping a body. Closures (`browse()`) already give scoping and cleanup in script and templates, so v1 ships **no components**.

The one real body use case, for later: render body content with Chromium, e.g. HTML to PDF or image in templates:

```html
<bx:playwrightRender type="pdf" path="invoice.pdf" format="A4">
    <h1>Invoice #bx:output#...</h1>
</bx:playwrightRender>
```

This overlaps with bx-pdf, so it is only worth it if Chromium rendering (modern CSS, JS) is a real need. Decide after v1.

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
- [ ] `box.json` `boxlang.executable` (`bxPlaywright`) and `boxlang.completions`.
- [ ] `main()` / `dispatch()` / verb registry (bx-agents pattern), `help`, `--version`, exit codes via `CLIExit`.
- [ ] Verbs: `install`, `install-deps`, `uninstall`, `doctor`, `version`, `clean`, `run`.
- [ ] Verbs: `codegen`, `open`, `show-trace`, `screenshot`, `pdf`, `mcp`, `devices`.
- [ ] Build step that generates `completions/bxPlaywright.bash` from the verb registry.
- [ ] CLI specs calling `dispatch()` in-process.
- [ ] GitHub Action example for CI (install with deps, cache browsers).

### Phase 2: Core DSL
- [ ] `OptionsMapper` with tests for every options class used.
- [ ] `Manager` (thread-confined, auto cleanup), `Browser`, `Context`, `Page`, `Locator` wrappers.
- [ ] Smart selector resolver.
- [ ] Assertions (inline + `pwExpect`), web-first, configurable timeout.
- [ ] Network (`intercept`, events), `request()` API testing, storage state `session()`, tracing, video, screenshots, PDF, clock.

### Phase 3: BIF, interceptors, testing SPI
- [ ] `playwright()` BIF, profiles, one-shot helpers (6.7).
- [ ] Interceptor events (6.9).
- [ ] Testing SPI: lifecycle scopes, artifact API and policies, device registry, webServer helper.
- [ ] Support the TestBox team building the adapter in TestBox core (tracked in the TestBox repo).

### Phase 4: Advanced
- [ ] Page objects, components, macros.
- [ ] Devices and emulation modifiers.
- [ ] Quality checks: console/smoke, axe accessibility, aria snapshots.
- [ ] Visual regression (baseline + pixel diff + diff image).
- [ ] Evaluate `bx:playwrightRender` body component (6.8).
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

1. Executable casing: `bxPlaywright` (matches `bxSites`, `bxAgents`) or all lowercase `bxplaywright`?
2. Entry ideas in 6.7: named profiles, `visit()` shortcut, `connect()`. Keep all?
3. Is `bx:playwrightRender` (Chromium HTML to PDF/image) worth doing after v1, given bx-pdf?
4. Who on the TestBox side owns the adapter and the SPI contract?
