---
title: BoxLang Web Applications
order: 21
summary: Use bx-playwright from a BoxLang web server without a separate OS-level module installation.
tags: [web-applications, integration]
---

# BoxLang Web Applications

Use bx-playwright inside a BoxLang web application, for example to render PDFs or capture pages on request. The deployed module manages its own driver, Node.js runtime and browsers: you do not need a separate operating-system BoxLang installation or the CLI.

```js
playwrightEnsureBrowser( "chromium" )

pageTitle = playwright().browse( ( page ) => page.visit( "https://example.com" ).title() )
```

## Deploy the module to the server

Install or declare bx-playwright in the same BoxLang server runtime that hosts the application. Without it, `playwright()` and `playwrightEnsureBrowser()` are not available there.

## Provision a browser

`playwrightEnsureBrowser( browser, [channel] )` installs a missing browser (`chromium` by default, `firefox` or `webkit`) into the module's browser cache, and makes its driver and Node.js runtime available. It is safe to call repeatedly and returns the browser name, the cache path and the installed browser directories.

- The first call needs network access and takes longer.
- With a channel, such as `playwrightEnsureBrowser( "chromium", "chrome" )`, it uses the browser installed on the machine and downloads nothing.
- When an install fails, the `Playwright.NotInstalled` error carries the last lines of the Playwright CLI output in its `detail`.
- TestBox browser specs call it for you on first use, see [TestBox Browser Testing](testbox.md).

## Use it in requests

- Prefer `browse()` and the one-shot helpers (`screenshot()`, `pdf()`, `render()`, `content()`): they close the browser even when the code throws.
- A manager is not thread safe: create one per request or task, never share it in the application scope.
- On a web runtime, `request`, `url`, `form`, `cookie`, `session` and `cgi` resolve to BoxLang scopes, even as closure arguments: pick other names, such as `address` or `sent`.
- As a safety net, the module closes every manager still open when it unloads and when the JVM shuts down.

## Configure the server's home

By default, everything is stored under `~/.boxlang/playwright`, where `~` is the home directory of the **web-server process user**. Set a dedicated writable location in the server's module settings:

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

`browsersPath` defaults to `{home}/browsers` and can be set separately. The server user must be able to write to both. Provisioning from another runtime or operating-system user does not fill this server's home.

## Network and Linux dependencies

The BIF downloads browser files only. It does not run package managers or need elevated privileges.

- On Linux, install the browser's native libraries in the server image or host as part of deployment. Where package installation is allowed, the CLI offers `bxPlaywright install chromium --with-deps`; never run it from an application request.
- For offline deployments, provision the same bx-playwright home ahead of time. For TestBox, also add `@browserAutoInstall( false )`.

See [Configuration](configuration.md) for every path and setting.
