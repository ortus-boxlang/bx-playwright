---
title: TestBox Browser Testing
order: 20
summary: Run TestBox and ColdBox browser specs with the bx-playwright module deployed in a BoxLang server.
tags: [testing, testbox, integration]
---

# TestBox Browser Testing

TestBox 7.2+ and ColdBox 8.3+ run real browser tests from BoxLang specs. Add `@browser` to a spec and TestBox gives it a browser, browser matchers and failure artifacts. The browser runs on the server that executes TestBox, not in your own browser.

```boxlang
@browser
@browserProfile( "ci" )
@baseURL( "http://localhost:8080" )
class extends="testbox.system.BaseSpec" {

	function run() {
		describe( "Login", () => {
			it( "signs in", () => {
				browse( ( page ) => {
					page.visit( "/login" )
						.fill( "Email", "dev@example.com" )
						.fill( "Password", "secret" )
						.click( "Sign in" )

					expect( page ).toHavePath( "/dashboard" )
					expect( page ).toSee( "Welcome" )
				} )
			} )
		} )
	}

}
```

## Install the module in the server

Deploy bx-playwright to the BoxLang runtime that runs the TestBox web application. For a CommandBox server, install it when the server is first set up:

```json
{
	"scripts": {
		"onServerInitialInstall": "install bx-playwright --nosave"
	}
}
```

No separate operating-system BoxLang installation is needed: TestBox asks the deployed module to download its browser on first use, see [Provision the browser](#provision-the-browser).

## Browser specs

A spec gets browser support when its class, or a class it extends, has any of these annotations, so `@browser` is optional when another one is present:

| Annotation | Purpose |
|---|---|
| `@browser` | Turn on browser support |
| `@browserProfile( "ci" )` | The bx-playwright [profile](profiles.md) for the bundle |
| `@baseURL( "http://localhost:8080" )` | Relative URLs in `visit()` resolve against it |
| `@browserAutoInstall( false )` | Never download a missing browser, see [below](#provision-the-browser) |

TestBox mixes these methods into the spec, which calls them unqualified:

| Method | What it does |
|---|---|
| `browse( callback, options )` | Runs the callback with fresh, isolated pages, closes them, and attaches failure artifacts to the spec |
| `browserAvailable()` | True when browser specs can run here, handy for `skip = !browserAvailable()` |
| `ensureBrowserInstalled()` | Installs the profile's browser, for example from `beforeAll()` |
| `getBrowserSupport()` | The bundle's `testbox.system.browser.BrowserSupport` |
| `closeBrowser()` | Closes the bundle browser now |
| `this.playwright()` | The bundle's bx-playwright manager. An unqualified `playwright()` is still the bx-playwright BIF, which returns a new manager the bundle does not close |

- Methods the spec declares itself are kept.
- Browser matchers include `toSee()`, `toHaveTitle()`, `toHavePath()`, `toHaveText()`, `toBeVisible()` and `toHaveCount()`. Every bx-playwright [assertion](assertions.md) works too: `page.assertSee( "Welcome" )`.
- The bundle shares one browser, started on first use. The runner closes it after the bundle, even when `afterAll()` throws, so your `beforeAll()` and `afterAll()` need no cleanup or `super` calls.
- `testbox.system.BrowserSpec` is an optional base class: `testbox.system.BaseSpec` with `@browser`. Use the annotation when the spec already extends another base class.
- For logged-in specs, log in once with a [saved session](network.md#saved-sessions) and pass `{ session : "name" }` to `browse()`.
- Artifact policies come from the profile, see [Testing](testing.md#artifacts).

## ColdBox

ColdBox 8.3+ uses the same annotation on `coldbox.system.testing.BaseTestCase`, which adds `routeURL()`, `visitRoute()` and `assertRouteIs()` for named routes:

```boxlang
@appMapping( "/root" )
@browser
@baseURL( "http://localhost:8080" )
class extends="coldbox.system.testing.BaseTestCase" {

	function run() {
		describe( "Users", () => {
			it( "shows a user", () => {
				browse( ( page ) => {
					visitRoute( page, "users.show", { id : 5 } )
					assertRouteIs( page, "users.show" )
					expect( page ).toSee( "User 5" )
				} )
			} )
		} )
	}

}
```

See the [ColdBox Browser Testing guide](https://coldbox.ortusbooks.com/the-basics/testing-quick-start/browser-testing).

## Provision the browser

### Automatic (default)

When a spec first uses `browse()` or `this.playwright()`, TestBox asks the bx-playwright module loaded in the web server to install the profile's browser, using the module's own Node.js runtime and browser cache. The first run needs network access and takes longer; later runs reuse the cache.

### Before the first spec

Install from a lifecycle hook, for example when automatic installation is disabled:

```boxlang
@browser
@browserAutoInstall( false )
@browserProfile( "ci" )
class extends="testbox.system.BaseSpec" {

	function beforeAll() {
		ensureBrowserInstalled()
	}

	function run() {
		// Browser specs
	}

}
```

With `@browserAutoInstall( false )` and no browser installed, launching fails with setup guidance. Server code can call the underlying BIF directly: `playwrightEnsureBrowser( "chromium" )`.

### Preinstalled from the CLI

To download browsers before the web app starts, or when the server cannot reach the download host, install them from a standalone BoxLang installation into a shared cache:

```bash
install-bx-module bx-playwright
BX_PLAYWRIGHT_HOME=/var/cache/myapp-playwright bxPlaywright install chromium
```

- Point the server's module at the same cache: set its `home` to `/var/cache/myapp-playwright`, or the same `browsersPath` in both runtimes.
- Use the same bx-playwright version in the CLI and the server, so both expect the same browser revision.
- Make sure the server's operating-system user can read and write the cache.
- Add `@browserAutoInstall( false )` above the spec's `class` so a web request never downloads a browser.

## Runtime requirements

- The BoxLang web server loads the module under the `playwright` mapping.
- The server process can write to the bx-playwright home and browser cache.
- The first browser installation needs outbound network access unless the cache is already provisioned.
- On Linux, native browser libraries are a host or container dependency. TestBox does not install operating-system packages from a web request.

For CI, cache the server's bx-playwright home, or preinstall into the shared cache before the server starts. See [BoxLang Web Applications](web-applications.md) for server paths and permissions, and [Testing](testing.md#ci) for the standalone CLI workflow.
