---
title: Getting Started
order: 2
summary: Install the module, install a browser, run your first script.
tags: [setup]
---

# Getting Started

## Install

Requires BoxLang 1.17+ and Java 21+.

```bash
install-bx-module bx-playwright
bxPlaywright install              # driver + Node.js + Chromium
bxPlaywright doctor               # check everything
```

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
