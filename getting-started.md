---
title: Getting Started
order: 2
summary: Install the module, provision a browser, run your first script.
tags: [setup]
---

# Getting Started

## Install

Requires BoxLang 1.17+ and Java 21+.

### Standalone BoxLang runtime

Use the CLI when you are installing bx-playwright for a standalone BoxLang installation:

```bash
install-bx-module bx-playwright
bxPlaywright install              # driver + Node.js + Chromium
bxPlaywright doctor               # check everything
```

### BoxLang web application

Deploy bx-playwright to the BoxLang server that hosts the application. Call `playwrightEnsureBrowser( "chromium" )`
from that web runtime to provision the browser; a separate OS-level BoxLang installation or CLI is not required.
TestBox `BrowserSpec` uses this API automatically on first use unless `@browserAutoInstall( false )` is set. With
auto-install disabled, call `ensureBrowserInstalled()` in the bundle's `beforeAll()` to provision explicitly.
The first install needs network access; Linux system libraries remain the responsibility of the host or container.

See [BoxLang Web Applications](web-applications.md) for server deployment and [TestBox Browser Testing](testbox.md) for specs, profiles and failure artifacts.

More browsers: `bxPlaywright install firefox webkit`. On Linux CI, add `--with-deps` to install the system libraries browsers need.

Everything is stored in `~/.boxlang/playwright` (change it with the `home` setting or `BX_PLAYWRIGHT_HOME`).

## First script

```js
// hello.bxs
page = playwright().visit( "https://boxlang.io" )
println( page.title() )
page.screenshot( "boxlang.png" ).quit()
```

```bash
boxlang hello.bxs
```

## Clean up

`playwright()` starts nothing until you use it. When you are done:

- `browse()` and the one-shot helpers (`screenshot()`, `pdf()`, `content()`, `render()`) close everything for you.
- `page.close()` closes the page and its context; `page.quit()` or `manager.close()` closes everything.

```js
playwright().browse( ( page ) => {
	page.visit( "https://boxlang.io" ).assertSee( "BoxLang" )
} )
```

A manager is not thread safe: use it from the thread that created it.

## Next

- [Automation Scripts](automation.md): turn a script into a recorded, scheduled job.
- [Testing](testing.md): browser tests with TestBox and ColdBox.
