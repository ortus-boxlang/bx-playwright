---
title: BoxLang Web Applications
order: 18
summary: Use bx-playwright from a BoxLang web server without a separate OS-level module installation.
tags: [web-applications, integration]
---

# BoxLang Web Applications

bx-playwright can run inside a BoxLang web application. The module uses the driver and Node.js runtime it manages, then stores Playwright browser downloads in its configured home. You do not need to install BoxLang or bx-playwright separately at the operating-system level just to provision browsers for the web application.

## Deploy the module to the server

Install or declare bx-playwright in the same BoxLang server runtime that hosts the application. If the module is not present in that runtime, the `playwright()` and `playwrightEnsureBrowser()` APIs are not available there.

The CLI is one way to install a browser for a standalone BoxLang installation. A deployed web application does not need to use that separate CLI environment: call the BIF from the web server instead.

## Provision a browser

Call `playwrightEnsureBrowser()` from server-side BoxLang code before launching the browser:

```boxlang
playwrightEnsureBrowser( "chromium" )

var browser = playwright( { headless : true } )
try {
	var page = browser.newPage()
	page.visit( "https://example.com" )
	println( page.title() )
} finally {
	browser.close()
}
```

The BIF uses the deployed module's settings, downloads a missing browser into that module's browser cache, and can be called repeatedly. With a channel, such as `playwrightEnsureBrowser( "chromium", "chrome" )`, it uses the browser installed on the machine and downloads nothing. When an install fails, the `Playwright.NotInstalled` error carries the last lines of the Playwright CLI output in its `detail`. It also makes the module's driver and Node.js runtime available as needed. The first call can take longer and requires network access. For TestBox, browser specs (`@browser`) invoke this API automatically on first browser use; see [TestBox Browser Testing](testbox.md).

## Configure the server's home

By default, bx-playwright stores its driver, Node.js runtime, browsers, sessions and artifacts under `~/.boxlang/playwright`, where `~` is the home directory of the **web-server process user**. You can set a dedicated writable location in the BoxLang server's module settings:

```json
{
	"modules": {
		"playwright": {
			"settings": {
				"home": "/var/lib/myapp/playwright"
			}
		}
	}
}
```

`browsersPath` can be configured separately; otherwise it defaults to `{home}/browsers`. Make sure the server user can write to these locations. Provisioning from one runtime or user does not populate a different server runtime's custom home or a different OS user's home.

## Network and Linux dependencies

The BIF downloads browser files only. It does not run package managers or require elevated privileges. On Linux, install the browser's native shared-library dependencies in the server image or host as part of deployment. For an environment where package installation is permitted, the standalone CLI offers `bxPlaywright install chromium --with-deps`; do not invoke that privileged option from an application request.

For offline deployments, provision the same bx-playwright home ahead of time, or disable TestBox's automatic download with `@browserAutoInstall( false )` and ensure a compatible browser is already present. See [Configuration](configuration.md) for all module paths and settings.