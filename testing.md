---
title: Testing
order: 16
summary: Use bx-playwright in tests, keep artifacts on failure, debug.
tags: [testing]
---

# Testing

bx-playwright works in any test framework. TestBox and ColdBox build their browser testing support on the same public API, described in [Building on bx-playwright](integrations.md).

```js
describe( "Login", () => {
	it( "signs in", () => {
		playwright( "ci" ).browse( ( page ) => {
			page.visit( "http://localhost:8080/login" )
				.fill( "Email", "luis@ortus.com" )
				.fill( "Password", "secret" )
				.click( "Sign in" )
				.assertPathIs( "/dashboard" )
		} )
	} )
} )
```

`browse()` closes the context with `failed = true` when the callback throws, so failure artifacts are kept.

## Artifacts

The `artifacts` setting (or a profile such as `ci`, `record`, `debug`) records screenshots, traces and videos:

| Policy | Keeps |
|---|---|
| `off` | nothing |
| `on` | always |
| `only-on-failure` / `retain-on-failure` | only when the context closes with `failed = true` |

```js
context = pw.newContext( { artifacts : { trace : "retain-on-failure", screenshot : "only-on-failure" } } )
// ...
kept = context.close( failed = true )   // { screenshots, trace, videos, directory }
```

Open a trace with `bxPlaywright show-trace path/to/trace.zip`.

## Debugging

- `playwright( "debug" )`: headed, slowed down, every artifact on.
- `playwright( "headed" )` or `BX_PLAYWRIGHT_HEADLESS=false`.
- `page.snapshot()` prints the accessibility tree.
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

Replace `boxlang run-tests.bxs` with your test command. If your tests need the application running, start it in an earlier step, for example `boxlang-miniserver --port 8080 &`, and set `BX_PLAYWRIGHT_BASEURL`. Artifacts go to `~/.boxlang/playwright/artifacts` unless you set `artifacts.directory`. Open a downloaded trace with `bxPlaywright show-trace trace.zip`.
