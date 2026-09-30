# bx-playwright: Research and Consolidated Plan

Status: v10. Phases 0 to 3 are implemented and tested (see the checkboxes in section 9). Phases 4 to 7 are next.

## 1. Goal

One BoxLang module that replaces both `cbPlaywright` and `commandbox-cbplaywright`:

- Ships the Playwright Java bindings and manages the driver and browsers itself.
- Provides a CLI (install browsers, codegen, show-trace, etc.).
- Offers a fluent BoxLang DSL usable anywhere: tests, scheduled tasks, scraping, PDF/screenshot generation.
- Is consumed by TestBox core through its public API (BIF and component) for base specs, matchers and failure artifacts.

## 2. Research Summary

### 2.1 Playwright Java (latest 1.63.0, Sept 2026)

- Maven artifacts (`com.microsoft.playwright`):
  - `playwright` (~0.7 MB): the Java API.
  - `driver` (~3.2 MB): the playwright-core JS bundle (`cli.js`).
  - `driver-bundle` (~204 MB): Node binaries for every platform. Optional if `PLAYWRIGHT_NODEJS_PATH` points to a Node runtime.
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
3. **Two modules on ForgeBox, one source tree** (see 4.1):
   - **`bx-playwright`**: small (Java API + driver JS, ~4 MB). `bxPlaywright install` fetches the Node runtime for the current OS/arch, then browsers. Fast and nimble.
   - **`bx-playwright-full`**: includes `driver-bundle` (~204 MB, Node for every platform). Works without downloading Node; browsers still install via the CLI.
4. **TestBox adapter lives in TestBox core.** The contract is bx-playwright's public surface: the `playwright()` BIF (and the fluent objects it returns) plus the `bx:playwrightRender` component. No separate testing SPI (see 7).
5. **One module, `bx-playwright`**, registered as `playwright`; main entry BIF `playwright()`.
6. **Targets BoxLang 1.17.x on JRE 21.**
7. **Own CLI, no CommandBox.** A `bxPlaywright` executable plus bash completions, using the module descriptor `boxlang.executable` / `boxlang.completions` fields (same pattern as bx-sites and bx-agents). See 5.
8. **Both assertion styles**: fluent inline (`page.assertSee()`) and expect style (`expect( locator ).toBeVisible()`).
9. **Components only where a body is needed.** One component: `bx:playwrightRender` (see 6.8).
10. **Executable is `bxPlaywright`**, matching `bxSites` and `bxAgents`.

Proposed (not yet confirmed):

11. **Single version source**: the Playwright version comes from the bundled jars at build time (Gradle). Driver and Node always match it.
12. **Persistent home** `~/.boxlang/playwright/` (overridable): `driver/` (extracted once from the bundled jars via `CLI install-driver`) and `browsers/`. The module sets `PLAYWRIGHT_DRIVER_DIR`, `PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` so nothing is extracted per launch and browsers install only via the CLI.
13. **Thread confinement built in**: the DSL never shares a Java `Playwright` across threads. A per-thread manager (and an optional pool for web/scheduler use).

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
      Profiles.bx            built-in profiles + settings merge (see 6.10, 6.11)
  src/main/java/             only if needed (e.g. image diff, event bridging)
  libs/                      playwright, driver jars (+ gson, etc.); driver-bundle only in the full build
```

### 4.1 Two distributions

| | `bx-playwright` | `bx-playwright-full` |
|---|---|---|
| Size | ~4 MB | ~208 MB |
| Node runtime | Downloaded by `bxPlaywright install` for the current OS/arch | Bundled (all platforms) |
| Browsers | `bxPlaywright install` | `bxPlaywright install` |
| Best for | Developers, CI with caching, Docker images | Air-gapped or locked-down networks, zero-setup |

- Same source, same version, released together. Gradle builds two zips (`-Pflavor=small|full`); the full build only adds the `driver-bundle` jar and sets `bundledNode: true` in the module.
- Both register as module `playwright` with executable `bxPlaywright`, so code, settings, profiles and docs are identical. Install one or the other; `doctor` warns if both are present.
- **Node resolution** (small module), first match wins:
  1. `nodePath` setting or `PLAYWRIGHT_NODEJS_PATH`.
  2. Previously installed runtime in `{home}/node/`.
  3. `node` on the system PATH, if its version meets Playwright's minimum.
  4. Otherwise `bxPlaywright install` downloads the official Node build for the OS/arch (linux x64/arm64, macOS x64/arm64, windows x64) into `{home}/node/`, verifies its checksum, with a configurable mirror (`nodeDownloadURL`) for proxies.
- If a script runs before install, it fails fast with `Playwright.NotInstalled` and the exact command to run.
- Pinned Node version per release: the version Playwright bundles for that release (recorded at build time), to be confirmed in the Phase 0 spike.

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
| `install [browsers...] [--with-deps] [--only-shell] [--force] [--skip-node]` | Set up the driver and Node runtime (small module downloads Node), then install browsers |
| `install-node [--force]` | Only the Node runtime (small module) |
| `install-deps [browsers...]` | OS dependencies (Linux) |
| `uninstall [--all]` | Remove browsers |
| `doctor [--json]` | Versions, paths, platform, Node, installed browsers, fix hints |
| `version` / `--version` | Module, Playwright and browser versions |
| `codegen [url] [--device --viewport --load-storage --save-storage]` | Record actions (Java target for now; BoxLang target later, see 10) |
| `open [url]` | Open a headed browser |
| `screenshot <url> <file> [--full-page --device]` | One-shot screenshot |
| `pdf <url> <file> [--format]` | One-shot PDF (Chromium) |
| `show-trace [file]` | Trace viewer |
| `mcp [options]` | Start the Playwright MCP server (bundled in driver) |
| `devices [--json]` | List device descriptors |
| `profiles [name] [--json]` | List profiles or show one fully resolved |
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

More entry points:

- `playwright( "mobile" )`: a string argument is a profile name (see 6.11). Browser names are also built-in profiles, so `playwright( "firefox" )` works the same way.
- `playwright( [ "mobile", "dark" ] )`: profiles merge left to right.
- `playwright( "mobile", { locale: "es-ES" } )`: profile plus per-call overrides.
- `playwright().visit( url )`: fastest path to a page (lazy launch of browser, context and page).
- `playwright().request()`: API testing without a browser.
- `playwright().connect( wsEndpoint )` / `connectOverCDP( url )`: remote browsers, Docker, grids.
- `playwright().expect( locatorOrPage )`: expect-style assertions outside TestBox.

### 6.8 Components

Rule: only when wrapping a body. Closures (`browse()`) already give scoping and cleanup, so the only component is one that needs a body.

**`bx:playwrightRender`**: renders the body content with Chromium (modern CSS, JS, web fonts) to PDF or image.

```html
<bx:playwrightRender type="pdf" path="invoice.pdf" format="A4" landscape="false">
    <h1>Invoice #invoice.id#</h1>
    ...
</bx:playwrightRender>

<bx:playwrightRender type="png" variable="imageBytes" viewport="1200x630" fullPage="true">
    ...social card HTML...
</bx:playwrightRender>
```

Proposed attributes: `type` (pdf, png, jpeg, webp), `path` or `variable` (bytes), `baseURL` (resolves relative assets), `waitFor` (selector or `networkidle`), PDF options (`format`, `landscape`, `margin`, `headerTemplate`, `footerTemplate`, `printBackground`), image options (`viewport`, `fullPage`, `device`, `omitBackground`). Same capability is available in script as `playwright().render( html, options )`.

### 6.9 Interceptors (extension points)

Announced events so any module can hook in without subclassing:
`onPlaywrightCreate`, `onBrowserLaunch`, `onContextCreate`, `onPageCreate`, `onPageClose`, `onPlaywrightAssertionFailure`, `onPlaywrightArtifact` (screenshot, trace or video written).

### 6.10 Configuration (module settings)

Every setting has a default in `ModuleConfig.configure()` and can be overridden in `boxlang.json`:

```json
"modules": {
    "playwright": {
        "settings": {
            "headless": false,
            "baseURL": "http://localhost:8080",
            "profiles": { "staging": { "extends": "desktop", "baseURL": "https://staging.site.com" } }
        }
    }
}
```

| Setting | Default | Purpose |
|---|---|---|
| `home` | `~/.boxlang/playwright` | Root for the extracted driver and browsers |
| `browsersPath` | `{home}/browsers` | Sets `PLAYWRIGHT_BROWSERS_PATH` |
| `nodePath` | `""` | Explicit Node override |
| `nodeDownloadURL` | official Node distribution URL | Mirror for Node downloads (small module) |
| `defaultProfile` | `"default"` | Profile used by `playwright()` with no arguments |
| `browser` | `"chromium"` | chromium, firefox, webkit |
| `channel` | `""` | chrome, chrome-beta, msedge (branded browsers) |
| `headless` | `true` | Headless or headed |
| `slowMo` | `0` | Milliseconds between actions (debugging) |
| `baseURL` | `""` | Relative URLs in `visit()` resolve against it |
| `viewport` | `{ width: 1280, height: 720 }` | Default viewport |
| `device` | `""` | Device descriptor name (overrides viewport, user agent, touch) |
| `locale` / `timezone` | `""` | Emulation |
| `colorScheme` | `"light"` | light, dark, no-preference |
| `ignoreHTTPSErrors` | `false` | Self-signed certs in dev |
| `timeouts` | `{ action: 30000, navigation: 30000, assertion: 5000 }` | Playwright defaults |
| `testIdAttribute` | `"data-testid"` | Used by `byTestId()` and the `@name` smart selector |
| `artifacts` | `{ directory: "{home}/artifacts", screenshot: "off", trace: "off", video: "off" }` | Policies: off, on, only-on-failure, retain-on-failure |
| `render` | `{ format: "A4", printBackground: true, waitUntil: "networkidle" }` | Defaults for `bx:playwrightRender` / `render()` |
| `launchOptions` | `{}` | Raw passthrough to `BrowserType.LaunchOptions` |
| `contextOptions` | `{}` | Raw passthrough to `Browser.NewContextOptions` |
| `profiles` | `{}` | User profiles, merged over the built-in ones |

Environment overrides for CI (confirmed prefix `BX_PLAYWRIGHT_*`, read at module load, win over settings): `BX_PLAYWRIGHT_PROFILE`, `BX_PLAYWRIGHT_BROWSER`, `BX_PLAYWRIGHT_HEADLESS`, `BX_PLAYWRIGHT_BASEURL`.

Resolution order, last wins: module settings, profile(s), environment overrides, per-call options. `BX_PLAYWRIGHT_PROFILE` picks the profile when none is requested. (Implemented order: environment variables win over profiles so CI can force a browser or headless mode.)

### 6.11 Profiles

A profile is a named set of the settings above. It can `extends` another profile. Users add or override profiles in `settings.profiles`; a user profile with a built-in name replaces that built-in.

Built-in profiles, grouped:

**Browsers**

| Profile | Settings |
|---|---|
| `default` | chromium, headless, 1280x720 |
| `chromium` / `firefox` / `webkit` | That browser, otherwise default |
| `chrome` / `chrome-beta` | chromium with `channel: "chrome"` / `"chrome-beta"` |
| `edge` | chromium with `channel: "msedge"` |

**Screens**

| Profile | Settings |
|---|---|
| `hd` | 1280x720 |
| `laptop` | 1366x768 |
| `macbook` | 1440x900, `deviceScaleFactor: 2` |
| `desktop` | 1920x1080 |
| `4k` | 3840x2160 |

**Devices** (Playwright device descriptors)

| Profile | Settings |
|---|---|
| `mobile` / `iphone` | webkit, `iPhone 15` |
| `iphone-se` | webkit, `iPhone SE` (small screen) |
| `mobile-landscape` | webkit, `iPhone 15 landscape` |
| `android` / `pixel` | chromium, `Pixel 7` |
| `galaxy` | chromium, `Galaxy S24` |
| `tablet` / `ipad` | webkit, `iPad Pro 11` |
| `android-tablet` | chromium, `Galaxy Tab S9` |

**Appearance and accessibility** (meant to be merged, e.g. `[ "mobile", "dark" ]`)

| Profile | Settings |
|---|---|
| `dark` / `light` | `colorScheme` |
| `reduced-motion` | `reducedMotion: "reduce"` |
| `high-contrast` | `forcedColors: "active"` |

**Modes**

| Profile | Settings |
|---|---|
| `headed` | `headless: false` |
| `debug` | headed, `slowMo: 250`, all artifacts `on` |
| `record` | video and trace `on` |
| `ci` | headless, screenshot `only-on-failure`, trace and video `retain-on-failure` |
| `offline` | context `offline: true` |
| `print` | chromium, headless, light, for PDF and render work |
| `screenshot` | chromium, `deviceScaleFactor: 2`, `reducedMotion: "reduce"`, `timezone: "UTC"`, `locale: "en-US"` (stable, repeatable images for visual diffs) |

Device names were validated against the Playwright 1.63 device registry (207 devices, read at runtime from the driver). Network throttling (e.g. `slow-3g`) is Chromium-only via CDP; considered for later.

Tooling: `bxPlaywright profiles` lists the resolved profiles; `doctor` shows the active settings after merge.

## 7. TestBox Integration (lives in TestBox core)

**Contract**: TestBox uses bx-playwright exactly like any other app does, through the `playwright()` BIF, the fluent objects it returns, and the `bx:playwrightRender` component. There is no private testing SPI. This keeps one API to document and version, and anything TestBox needs becomes a public feature everyone gets.

Consequences for bx-playwright's public API (needed so TestBox can build on it):

- **Explicit lifecycle**: `pw.newContext()`, `context.newPage()`, `close()` on every level, `pw.closeAll()`.
- **Artifact policies in the API**: `artifacts` settings (6.10) apply automatically, and `context.close( { failed: true|false } )` decides what is kept (e.g. `retain-on-failure` deletes on pass). Artifact paths are returned: `context.artifacts()` gives screenshot, trace and video paths.
- **Assertions throw a predictable error type** (`opentest4j.AssertionFailedError` or a BoxLang `Playwright.AssertionFailed`), so TestBox reports them as failures, not errors.
- **Profiles** (6.11) cover browser matrix and CI settings.
- **Interceptor events** (6.9) let TestBox or reporters react to artifacts.

### 7.1 TestBox core (we own it)

Framework-agnostic, built only on the `playwright()` BIF:

- Base specs or annotations that open a context per spec and close it with the spec result (artifact policies apply).
- Matchers: `expect( page ).toHaveTitle()`, `expect( locator ).toBeVisible()`, delegating to `playwright().expect()`.
- Artifacts linked from reporters and TestBox RUN; browser matrix via profiles; retries; `--failed` re-run of only failed specs.
- `webServer` option: boot the app (e.g. BoxLang miniserver) and wait for a URL before browser specs.
- Generic database helpers usable by any app: `databaseTruncation( tables )` and `databaseMigrations()` hooks that run before each browser spec. Note: transaction rollback (Laravel's `RefreshDatabase`) does not work for browser tests because the browser hits the app in a different request/thread; Dusk has the same limitation and uses truncation or migrations instead.

### 7.2 ColdBox (we own it)

A `ColdBoxBrowserSpec` in ColdBox's testing package that adds app-aware helpers on top of 7.1:

- **Auth**: `loginAs( userOrId )`, `logout()`, `assertAuthenticated()`, `assertAuthenticatedAs( user )`, `assertGuest()`. Works like Dusk: test-only routes registered when the app runs in a testing environment (never in production) log the user in through cbauth/cbsecurity; the resulting session is saved as storage state and reused.
- **Routes**: `visitRoute( "users.show", { id: 1 } )` and `assertRouteIs( "users.index" )` using ColdBox named routes.
- **Data**: factories and seeders on top of qb and cfmigrations (design to agree: new factory library or extend an existing Ortus one).
- **Boot**: `webServer` defaults for a ColdBox app, environment switched to `testing`.
- **Extras to consider**: `assertFlash( key )`, mail capture (cbmailservices) assertions.

## 8. AI Consumability

Goal: an AI agent (Claude, Copilot, Cursor, bx-ai agents) can discover, write and debug bx-playwright code correctly on the first try, and can drive a browser itself.

### 8.1 Skills (ortus-boxlang/skills)

New folder `boxlang-modules/bx-playwright/`, several focused skills (same layout as `bx-ai`):

| Skill | Covers |
|---|---|
| `bx-playwright-setup` | Install, `bxPlaywright` CLI verbs, settings, profiles, env vars, CI |
| `bx-playwright-browsing` | `playwright()`, `visit()`, actions, selectors, waits, frames, popups, downloads |
| `bx-playwright-assertions` | Inline `assert*` and `expect()` styles, timeouts, soft assertions |
| `bx-playwright-network` | `intercept()`, mocking, `request()` API testing, sessions and storage state |
| `bx-playwright-rendering` | `bx:playwrightRender`, `render()`, screenshots, PDF |
| `bx-playwright-testing` | Using it with TestBox, artifacts, debugging failures, page objects, components |
| `bx-playwright-ai` | Driving a browser from bx-ai agents and MCP (8.3) |

Repo updates in the same change: `boxlang-modules/README.md` table, root `README.md`, and the plugin manifests (`.claude-plugin/plugin.json` and `marketplace.json`, `.cursor-plugin/plugin.json` and `marketplace.json`, `.grok-plugin/plugin.json`): descriptions, keywords (`playwright`, `browser`, `e2e`, `testing`), version bump. The manifests already include `./boxlang-modules/` so the new folder is picked up automatically.

Skills are written last (Phase 7), against the shipped API. Every code sample in the skills is copied from a runnable example in this repo (`examples/`) that CI executes, so skills never drift from the code.

### 8.2 In the module itself

- **AGENTS.md** in the repo (architecture, conventions, how to add a verb/profile) and the skills pinned in `skills-lock.json`.
- **llms.txt** generated by bxSites from the docs, plus the docs site's MCP server (`mcp: true` in `bxsites.yaml`).
- **Predictable API**: one name per concept, consistent verbs (`visit`, `click`, `fill`, `assert*`), every action chainable, every option also accepted as a struct.
- **Docblocks with `@example`** on every public method, so DocBox output and IDE hovers teach the API.
- **Self-description**: `playwright().help()` and `page.help()` return the available methods and options as a struct/JSON.
- **CLI for machines**: `--json` on every verb, `help --json`, stable exit codes, non-interactive by default.
- **Errors that explain themselves**: typed errors (`Playwright.Timeout`, `Playwright.ElementNotFound`, `Playwright.AssertionFailed`, `Playwright.NotInstalled`), each message includes the selector, what was found instead, closest matches from the page, and a fix hint (e.g. "run `bxPlaywright install firefox`").
- **Page views for LLMs**: `page.snapshot()` returns the accessibility (aria) snapshot as compact YAML with element refs; `page.text()`, `page.links()`, `page.forms()` return structured data. Refs can be used in later calls (`page.click( ref: "e12" )`), which is how Playwright MCP lets models act reliably.
- **Artifacts as data**: every screenshot, trace and video call returns its path, so an agent can read or attach it.

### 8.3 Letting agents drive the browser

- **bx-ai tools**: `playwright().aiTools()` returns ready-made bx-ai tools (visit, snapshot, click, fill, select, screenshot, extract, close) so a bx-ai agent can browse with one line.
- **MCP**: `bxPlaywright mcp` starts Playwright's bundled MCP server for Claude, Cursor, etc. Evaluate exposing the same tools through bx-mcp.
- **Codegen to BoxLang** (Phase 5): record in a browser, get bx-playwright code an agent can refine.

### 8.4 Documentation (bxSites)

The module is documented with bxSites: `bxsites.yaml` at the root and Markdown in `docs/`. Simple and AI consumable: one topic per page, a runnable example first, then an options table. No marketing pages.

```
bxsites.yaml
docs/
  index.md               what it is, 30 second example
  getting-started.md     install, `bxPlaywright install`, first script, first test
  cli.md                 every verb and flag (mirrors `help --json`)
  configuration.md       settings table, env vars, resolution order
  profiles.md            built-in profiles, custom profiles, merging
  browsing.md            visit, actions, selectors, waits, frames, popups, downloads
  assertions.md          inline and expect styles
  network.md             intercept, mocking, request(), sessions
  rendering.md           bx:playwrightRender, render(), screenshots, PDF
  testing.md             TestBox usage, artifacts, debugging
  ai.md                  snapshot(), aiTools(), MCP
  errors.md              error type catalog with fixes
  nav.json
```

## 9. Phased Roadmap and Tasks

### Phase 0: Foundations
- [x] Run `SetupTemplate` (slug `bx-playwright`, mapping `playwright`), clean example BIFs/components.
- [x] AGENTS.md for the module; typed error catalog.
- [x] Gradle: `playwright` + `driver` deps into `libs/`; `full` flavor adds `driver-bundle`. Build two zips, two `box.json` slugs (`bx-playwright`, `bx-playwright-full`), stamp version.
- [x] Release workflow publishes both to ForgeBox together.
- [x] Full zip size: not a ForgeBox concern. Both zips go to S3 in one upload; ForgeBox only receives the two box.json files, whose `location` points to S3.
- [x] Spike: load jars in the module classloader, create `Playwright` with `PLAYWRIGHT_DRIVER_DIR` and the bundled Node. Confirm thread confinement behavior under BoxLang.
- [x] Spike: find a stable way to ship device descriptors (extract from driver bundle at build time) and validate built-in profile device names.

### Phase 1: Install and CLI
- [x] `PlaywrightService`: home resolution, one-time driver extraction (`install-driver`), env wiring.
- [x] Node resolution and download (4.1): OS/arch detection incl. arm64, checksum, mirror, `install-node` verb.
- [x] `box.json` `boxlang.executable` (`bxPlaywright`) and `boxlang.completions`.
- [x] `main()` / `dispatch()` / verb registry (bx-agents pattern), `help`, `--version`, exit codes via `CLIExit`, `--json` on every verb.
- [x] Verbs: `install`, `install-deps`, `uninstall`, `doctor`, `version`, `clean`, `run`.
- [x] Verbs: `codegen`, `open`, `show-trace`, `screenshot`, `pdf`, `mcp`, `devices`.
- [x] Build step that generates `completions/bxPlaywright.bash` from the verb registry.
- [x] CLI specs calling `dispatch()` in-process.
- [x] CI: browser tests on Linux with a cached playwright home.
- [ ] Documented GitHub Action example for users (install with deps, cache browsers).

### Phase 2: Core DSL
- [x] `OptionsMapper` with tests for every options class used.
- [x] `Manager` (thread-confined, auto cleanup), `Browser`, `Context`, `Page`, `Locator` wrappers.
- [x] Smart selector resolver.
- [x] Assertions (inline + `playwright().expect()`), web-first, configurable timeout, predictable failure type.
- [x] Network (`intercept`, events), `request()` API testing, tracing, video, screenshots, PDF.
- [x] Storage state `session( name, setup )` caching and `clock()` / `freezeTime()` helpers.

### Phase 3: BIF, config, profiles, interceptors
- [x] Module settings and resolution order (6.10); env overrides.
- [x] Built-in profiles, `extends`, merging, `profiles` CLI verb (6.11).
- [x] `playwright()` BIF, one-shot helpers (6.7).
- [x] `bx:playwrightRender` component and `playwright().render()` (6.8).
- [x] Interceptor events (6.9).
- [x] Artifact policies and `close( { failed } )` semantics, `artifacts()` paths (7).
- [ ] Hand off to TestBox core: document the public API it builds on.

### Phase 4: Advanced
- [x] Page objects, components, macros.
- [x] Devices and emulation modifiers.
- [x] Quality checks: console/smoke, axe accessibility, aria snapshots.
- [x] Visual regression (baseline + pixel diff + diff image).
- [x] Multi-user `browse()`.
- [x] Soft assertions.

### Phase 5: Tooling and AI features
- [x] BoxLang codegen target (post-process Java codegen output, or custom recorder). Done: `codegen --target boxlang` translates the Java output.
- [x] `examples/` recipes, executed in CI (source of truth for docs and skills samples).
- [x] `page.snapshot()` with refs, `help()` introspection, `aiTools()` for bx-ai, MCP verb (8.2, 8.3).

### Phase 6: Documentation (bxSites)
- [x] `bxsites.yaml` at the repo root, `source: docs`, search on, `mcp: true` (docs MCP server), repo edit links. Keep theme config minimal.
- [x] `docs/` pages, short and task focused (see 8.4).
- [x] llms.txt is generated by bxSites on build; verify it lists every page.
- [x] CI job that builds the site with `bxSites build` and fails on broken links.

### Parallel track (TestBox and ColdBox repos, after Phase 3)
- [ ] TestBox core: browser base specs, matchers, artifacts in reporters, webServer, database truncation/migrations hooks, `--failed` re-run (7.1).
- [ ] ColdBox: `ColdBoxBrowserSpec`, `loginAs()` and auth assertions via test-only routes, named route helpers, factories and seeders (7.2).

### Phase 7: Skills (last)
- [x] Write the 7 skills in ortus-boxlang/skills `boxlang-modules/bx-playwright/` from the shipped API and `examples/` (8.1).
- [x] Update `boxlang-modules/README.md`, root README, and all plugin manifests (Claude, Cursor, Grok).
- [x] Pin the skills in this repo's `skills-lock.json`.

## 10. Risks and Unknowns

- Codegen emits Java/JS/Python/.NET only. A BoxLang target needs translation of Java output or a custom recorder. Unverified effort.
- Device descriptors are not a public Java API; extraction path from the driver bundle needs a spike.
- Visual diff needs a pixel comparison implementation (Java has only `screenshot()`).
- Thread confinement vs BoxLang web requests and async: needs design validation in the Phase 0 spike.
- `bx-playwright-full` is ~208 MB. Confirm ForgeBox size limits.
- Small module depends on the Node download site (or a mirror) being reachable at install time; Playwright's minimum Node version must be tracked per release.
- TestBox builds on the public API from another repo with its own release cycle: the public API must follow semver strictly from 1.0.
- TestBox retries and artifact attachment to results: confirm what TestBox 7 exposes.

## 11. Open Questions

None right now. Decided since v6: skills are written in the last phase (7), docs use bxSites in phase 6. This plan moved from `docs/PLAN.md` to the repo root so `docs/` is reserved for the bxSites source.
