---
title: Getting Started
order: 2
summary: Install the module, provision a browser, run your first script.
tags: [setup]
---

# Getting Started

Install the module, download a browser and run a first script. Requires BoxLang 1.17+ and Java 21+.

## Install

```bash
install-bx-module bx-playwright
bxPlaywright install              # driver + Node.js + Chromium
bxPlaywright doctor               # check everything
```

- More browsers: `bxPlaywright install firefox webkit`.
- On Linux CI, add `--with-deps` to also install the system libraries browsers need.
- Everything is stored in `~/.boxlang/playwright`. Change it with the `home` setting or `BX_PLAYWRIGHT_HOME`.
- `bx-playwright-full` bundles Node.js for every platform, so `install` skips the Node.js download. Browsers are still downloaded unless they are already installed.

!!! tip "Running inside a BoxLang web server?"
    Deploy the module to that server and call `playwrightEnsureBrowser( "chromium" )` from server code: no separate CLI install is needed. TestBox browser specs do this for you on first use. See [BoxLang Web Applications](web-applications.md) and [TestBox Browser Testing](testbox.md).

### Browser download problems

- Browsers download from Playwright's CDN (`https://cdn.playwright.dev`). Behind a proxy, set `HTTPS_PROXY` before running `bxPlaywright install`.
- On a slow network, raise `PLAYWRIGHT_DOWNLOAD_CONNECTION_TIMEOUT` (milliseconds, `120000` by default), see [Configuration](configuration.md#environment-variables).
- To skip the Chromium download, use an installed Google Chrome with the `chrome` [profile](profiles.md): `playwright( "chrome" )`.
- Run `bxPlaywright doctor` to see what is installed and how to fix what is missing.

## First script

```js
// hello.bxs
playwright().browse( ( page ) => {
	page.visit( "https://boxlang.io" )
	println( page.title() )
	page.screenshot( "boxlang.png" )
} )
```

```bash
boxlang hello.bxs
```

`playwright()` returns a manager and starts nothing until you use it. `browse()` opens a fresh page, runs your code and closes everything, even when the code throws.

## Clean up

Prefer `browse()` and the one-shot helpers (`screenshot()`, `pdf()`, `content()`, `render()`): they close the browser for you. When you manage pages yourself, close them:

```js
pw   = playwright()
page = pw.newPage()
try {
	page.visit( "https://boxlang.io" ).assertSee( "BoxLang" )
} finally {
	pw.close()
}
```

- `page.close()` closes the page and its context; the manager stays open.
- `page.quit()` or `pw.close()` closes everything: pages, contexts, browser and driver.
- A manager is not thread safe: use it from the thread that created it.

## Next

- [Browsing](browsing.md): selectors, actions and locators.
- [Assertions](assertions.md): checks that wait for the page.
- [TestBox Browser Testing](testbox.md): browser specs with `@browser`.
- [Automation Scripts](automation.md): turn a script into a recorded, scheduled job.
