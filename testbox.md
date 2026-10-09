---
title: TestBox Browser Testing
order: 17
summary: Run BrowserSpec tests with the bx-playwright module deployed in a BoxLang server.
tags: [testing, testbox, integration]
---

# TestBox Browser Testing

TestBox's `testbox.system.BrowserSpec` integrates with bx-playwright to run real browser tests from BoxLang specs. The browser runs on the server that executes TestBox, not on the developer's browser.

## Install the module in the server

Deploy bx-playwright to the BoxLang runtime that runs the TestBox web application. For a CommandBox-managed server, the server configuration can install the module during initial setup:

```json
{
	"scripts": {
		"onServerInitialInstall": "install bx-playwright --nosave"
	}
}
```

This deploys the module for that server. For the normal web-app workflow, no separate OS-level BoxLang installation is needed: BrowserSpec asks this deployed module to provision its browser.

## Write a browser spec

```boxlang
@baseURL( "http://localhost:8080" )
@browserProfile( "ci" )
class extends="testbox.system.BrowserSpec" {

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

`@browserProfile` selects the bx-playwright profile for the bundle. The selected browser is installed automatically the first time `BrowserSpec` needs it. The deployed module downloads it into its configured browser cache; later runs reuse it. The first use can take longer and needs network access.

`browse()` creates isolated contexts and pages, closes them after each callback, and attaches configured failure artifacts to the spec. The bundle's browser matchers include `toSee()`, `toHaveTitle()`, `toHavePath()`, `toHaveText()`, `toBeVisible()` and `toHaveCount()`. See [Testing](testing.md) for artifact policies and [Assertions](assertions.md) for the full matcher surface.

## Choose how to provision the browser

There are two supported setup paths. The first is the simplest for most developers; the second is useful when browser downloads must happen before the web app runs, or the server cannot reach the download host.

### Let the deployed web-server module install it

This is the default. When a spec first uses `browse()` or `this.playwright()`, BrowserSpec asks the bx-playwright module loaded in that web server to ensure the profile's browser is installed. The module uses its own Node.js runtime and browser cache. No OS-level BoxLang or CLI installation is involved.

The first use needs network access and may take longer while the browser downloads. Subsequent runs reuse the browser cache.

### Preinstall from an OS-level BoxLang CLI

If you prefer to provision before starting the web app, you can install BoxLang and bx-playwright in a standalone OS-level BoxLang environment and run the CLI there:

```bash
install-bx-module bx-playwright
BX_PLAYWRIGHT_HOME=/var/cache/myapp-playwright bxPlaywright install chromium
```

The web server must then use the same browser cache. For example, configure the deployed module's `home` to `/var/cache/myapp-playwright`, or set the same `browsersPath` in both runtimes. Make sure both processes can access the cache, and use the same bx-playwright/Playwright version in the CLI and web server so they expect the same browser revision. If the server runs as a different OS user, check its filesystem permissions.

When using this preinstall route, put `@browserAutoInstall( false )` above the BrowserSpec class to prevent a browser download from a web request.

If the cache is not shared with the server or the Playwright versions differ, the preinstalled browser may not be found or may not match the server module. In that case, configure a shared cache or use the deployed-module route above.

## Provision explicitly or disable downloads

Use the public `ensureBrowserInstalled()` method to provision from a bundle lifecycle hook. This is useful when you want installation to happen before the first spec, or when automatic installation is disabled:

```boxlang
@browserAutoInstall( false )
@browserProfile( "ci" )
class extends="testbox.system.BrowserSpec" {

	function beforeAll() {
		ensureBrowserInstalled()
	}

	function run() {
		// Browser specs
	}

}
```

By default, `browserAutoInstall` is true. Set `@browserAutoInstall( false )` to prevent BrowserSpec from downloading a missing browser. If it is disabled and no browser is provisioned, launch fails with setup guidance.

The underlying BIF is also available directly to server-side BoxLang code:

```boxlang
playwrightEnsureBrowser( "chromium" )
```

See [BoxLang Web Applications](web-applications.md) for server runtime, cache permissions, and direct application usage.

## Runtime requirements

- The BoxLang web server must load the module under the `playwright` mapping.
- The server process must be able to write to the configured bx-playwright home and browser cache.
- The first browser installation needs outbound network access unless the cache is already provisioned.
- On Linux, native browser libraries are a host or container dependency. BrowserSpec does not install operating-system packages from a web request.

For CI, either cache the server's bx-playwright home or preinstall into the shared cache before the server starts. The standalone CLI workflow is documented in [Testing and CI](testing.md#ci).