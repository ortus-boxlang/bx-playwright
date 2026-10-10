---
title: Testing
order: 16
summary: Use bx-playwright in any test runner, keep artifacts on failure, debug, run in CI.
tags: [testing]
---

# Testing

Use bx-playwright from any test runner, keep screenshots, traces and videos of failed tests, debug them, and run them in CI.

!!! tip "Using TestBox or ColdBox?"
    Add `@browser` to the spec and the framework manages the browser, adds browser matchers such as `expect( page ).toSee( "Welcome" )` and attaches failure artifacts to the report. See [TestBox Browser Testing](testbox.md). This page covers what works everywhere, including inside those specs.

Without framework support, call `browse()` in each test:

```js
describe( "Login", () => {
	it( "signs in", () => {
		playwright( "ci", { baseURL : "http://localhost:8080" } ).browse( ( page ) => {
			page.visit( "/login" )
				.fill( "Email", "luis@ortus.com" )
				.fill( "Password", "secret" )
				.click( "Sign in" )
				.assertPathIs( "/dashboard" )
		} )
	} )
} )
```

When the callback throws, `browse()` closes the context with `failed = true`, so the `ci` profile keeps the failure artifacts. For logged-in tests, log in once with a [saved session](network.md#saved-sessions) and pass `{ session : "name" }` to `browse()`.

## Artifacts

The `artifacts` setting (or a profile such as `ci`, `record`, `debug`) records screenshots, traces and videos:

| Policy | Keeps |
|---|---|
| `off` | nothing |
| `on` | always |
| `only-on-failure` / `retain-on-failure` | only when the context closes with `failed = true` |

`browse()` applies the policies for you. When you manage a context yourself, tell `close()` whether the work failed:

```js
context = playwright().newContext( { artifacts : { trace : "retain-on-failure", screenshot : "only-on-failure" } } )
// ... run the test with context.newPage()
kept = context.close( failed = true )   // { screenshots, trace, videos, directory }
```

Artifacts go to `{home}/artifacts` unless you set `artifacts.directory`. Open a trace with `bxPlaywright show-trace path/to/trace.zip`.

## Debugging

- `playwright( "debug" )`: headed, slowed down, every artifact on.
- `playwright( "headed" )` or `BX_PLAYWRIGHT_HEADLESS=false`.
- `println( page.snapshot() )` prints the accessibility tree: what the page exposes to selectors.
- `bxPlaywright codegen http://localhost:8080` records your clicks as BoxLang code.

## CI

```bash
install-bx-module bx-playwright
bxPlaywright install chromium --with-deps
BX_PLAYWRIGHT_PROFILE=ci boxlang run-tests.bxs
```

Cache `~/.boxlang/playwright` between runs to skip the downloads.

### GitHub Actions

A complete workflow: it installs BoxLang and the module, caches the driver, Node.js and browsers, runs the tests with the `ci` profile, and uploads the screenshots, traces and videos of failed tests.

```yaml
name: Browser Tests

on: [ push, pull_request ]

jobs:
  browser-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - uses: ortus-boxlang/setup-boxlang@main
        with:
          version: latest
          modules: bx-playwright

      # Driver, Node.js and browsers: one download per bx-playwright version
      - name: Read the bx-playwright version
        id: pw
        run: echo "version=$( jq -r .version ~/.boxlang/modules/bx-playwright/box.json )" >> "$GITHUB_OUTPUT"

      - uses: actions/cache@v4
        with:
          path: ~/.boxlang/playwright
          key: playwright-${{ runner.os }}-${{ steps.pw.outputs.version }}

      # --with-deps installs the system libraries, which are not cached
      - name: Install Chromium
        run: |
          export PATH="$HOME/.boxlang/bin:$PATH"
          bxPlaywright install chromium --with-deps
          bxPlaywright doctor

      - name: Run tests
        env:
          BX_PLAYWRIGHT_PROFILE: ci
        run: |
          export PATH="$HOME/.boxlang/bin:$PATH"
          boxlang run-tests.bxs

      - name: Upload failure artifacts
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-artifacts
          path: ~/.boxlang/playwright/artifacts
          if-no-files-found: ignore
```

Replace `boxlang run-tests.bxs` with your test command. If your tests need the application running, start it in an earlier step, for example `boxlang-miniserver --port 8080 &`, and set `BX_PLAYWRIGHT_BASEURL`. Open a downloaded trace with `bxPlaywright show-trace trace.zip`.

For TestBox specs that run inside a web server, the server provisions its own browser: see [TestBox Browser Testing](testbox.md#provision-the-browser).
