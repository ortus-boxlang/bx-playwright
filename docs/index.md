---
title: BoxLang Playwright
order: 1
summary: Fluent browser automation and testing for BoxLang, powered by Microsoft Playwright.
tags: [overview]
---

# BoxLang Playwright

Drive Chromium, Firefox and WebKit from BoxLang: test web apps, mock the network, test APIs, check accessibility, compare screenshots, and render HTML to PDF or images.

```js
playwright().visit( "https://boxlang.io" )
	.assertTitleContains( "BoxLang" )
	.click( "Docs" )
	.screenshot( "docs.png" )
	.quit()
```

## What you get

| Piece | What it is |
|---|---|
| `playwright()` | The BIF that returns a manager: pages, contexts, one-shot helpers |
| Page and Locator | Chainable actions, smart selectors, web-first assertions |
| `bx:playwrightRender` | A component that renders its body to PDF, PNG, JPEG or WebP |
| `bxPlaywright` | The CLI: install browsers, doctor, codegen to BoxLang, trace viewer, MCP |

## Two distributions

| Module | Size | Node.js |
|---|---|---|
| `bx-playwright` | ~4 MB | downloaded by `bxPlaywright install` |
| `bx-playwright-full` | ~206 MB | bundled for every platform, works offline |

Same module name (`playwright`), same API. Install one of them.

Next: [Getting Started](getting-started.md).
