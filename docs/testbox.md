---
title: TestBox Browser Testing
order: 17
summary: Run TestBox browser specs with the bx-playwright module deployed in a BoxLang server.
tags: [testing, testbox, integration]
---

# TestBox Browser Testing

TestBox 7.2+ integrates with bx-playwright to run real browser tests from BoxLang specs. Add the `@browser` annotation to any spec and TestBox gives it a browser. The browser runs on the server that executes TestBox, not on the developer's browser.

## Install the module in the server

Deploy bx-playwright to the BoxLang runtime that runs the TestBox web application. For a CommandBox-managed server, the server configuration can install the module during initial setup:

```json
{
	"scripts": {
		"onServerInitialInstall": "install bx-playwright --nosave"
	}
}
```

This deploys the module for that server. For the normal web-app workflow, no separate OS-level BoxLang installation is needed: TestBox asks this deployed module to provision its browser.

## Write a browser spec

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

Any spec whose class, or a class it extends, has `@browser`, `@browserProfile` or `@baseURL` gets browser support, so `@browser` is optional when one of the others is present. On BoxLang, TestBox mixes these methods into the spec, which calls them unqualified:

| Method | What it does |
|---|---|
| `browse( callback, options )` | Runs the callback with fresh, isolated browser pages |
| `browserAvailable()` | True when browser specs can run here, handy for `skip = !browserAvailable()` |
| `ensureBrowserInstalled()` | Installs the profile's browser, for example from `beforeAll()` |
| `getBrowserSupport()` | The bundle's `testbox.system.browser.BrowserSupport` |
| `closeBrowser()` | Closes the bundle browser now |
| `this.playwright()` | The bundle's bx-playwright manager. An unqualified `playwright()` is still the bx-playwright BIF, which returns a new manager the bundle does not close |

Methods the spec declares itself are kept. TestBox also registers the browser matchers for every spec of the bundle.

`testbox.system.BrowserSpec` still exists as an optional base class: it is `testbox.system.BaseSpec` with `@browser`, so `class extends="testbox.system.BrowserSpec"` works the same. Use the annotation when the spec already extends another base class, such as ColdBox's `coldbox.system.testing.BaseTestCase`.

The bundle shares one browser, started on first use. The runner closes it after the bundle, even when `afterAll()` throws, so your own `beforeAll()` and `afterAll()` need no cleanup or `super` calls.

### ColdBox

ColdBox 8.3+ browser tests use the same annotation on `coldbox.system.testing.BaseTestCase`, which adds `routeURL()`, `visitRoute()` and `assertRouteIs()` for named routes:

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

### Profiles and first use

`@browserProfile` selects the bx-playwright profile for the bundle. The selected browser is installed automatically the first time the spec needs it. The deployed module downloads it into its configured browser cache; later runs reuse it. The first use can take longer and needs network access.

`browse()` creates isolated contexts and pages, closes them after each callback, and attaches configured failure artifacts to the spec. The bundle's browser matchers include `toSee()`, `toHaveTitle()`, `toHavePath()`, `toHaveText()`, `toBeVisible()` and `toHaveCount()`. See [Testing](testing.md) for artifact policies and [Assertions](assertions.md) for the full matcher surface.

## Choose how to provision the browser

There are two supported setup paths. The first is the simplest for most developers; the second is useful when browser downloads must happen before the web app runs, or the server cannot reach the download host.

### Let the deployed web-server module install it

This is the default. When a spec first uses `browse()` or `this.playwright()`, TestBox asks the bx-playwright module loaded in that web server to ensure the profile's browser is installed. The module uses its own Node.js runtime and browser cache. No OS-level BoxLang or CLI installation is involved.

The first use needs network access and may take longer while the browser downloads. Subsequent runs reuse the browser cache.

### Preinstall from an OS-level BoxLang CLI

If you prefer to provision before starting the web app, you can install BoxLang and bx-playwright in a standalone OS-level BoxLang environment and run the CLI there:

```bash
install-bx-module bx-playwright
BX_PLAYWRIGHT_HOME=/var/cache/myapp-playwright bxPlaywright install chromium
```

The web server must then use the same browser cache. For example, configure the deployed module's `home` to `/var/cache/myapp-playwright`, or set the same `browsersPath` in both runtimes. Make sure both processes can access the cache, and use the same bx-playwright/Playwright version in the CLI and web server so they expect the same browser revision. If the server runs as a different OS user, check its filesystem permissions.

When using this preinstall route, put `@browserAutoInstall( false )` above the spec's `class` to prevent a browser download from a web request.

If the cache is not shared with the server or the Playwright versions differ, the preinstalled browser may not be found or may not match the server module. In that case, configure a shared cache or use the deployed-module route above.

## Provision explicitly or disable downloads

Use the public `ensureBrowserInstalled()` method to provision from a bundle lifecycle hook. This is useful when you want installation to happen before the first spec, or when automatic installation is disabled:

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

By default, `browserAutoInstall` is true. Set `@browserAutoInstall( false )` to prevent TestBox from downloading a missing browser. If it is disabled and no browser is provisioned, launch fails with setup guidance.

The underlying BIF is also available directly to server-side BoxLang code:

```boxlang
playwrightEnsureBrowser( "chromium" )
```

See [BoxLang Web Applications](web-applications.md) for server runtime, cache permissions, and direct application usage.

## Runtime requirements

- The BoxLang web server must load the module under the `playwright` mapping.
- The server process must be able to write to the configured bx-playwright home and browser cache.
- The first browser installation needs outbound network access unless the cache is already provisioned.
- On Linux, native browser libraries are a host or container dependency. TestBox does not install operating-system packages from a web request.

For CI, either cache the server's bx-playwright home or preinstall into the shared cache before the server starts. The standalone CLI workflow is documented in [Testing and CI](testing.md#ci).