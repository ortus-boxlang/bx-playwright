---
title: Building on bx-playwright
order: 24
summary: The public contract that TestBox, ColdBox and other libraries build on.
tags: [reference, testing]
---

# Building on bx-playwright

Test frameworks, application frameworks and your own libraries build on `bx-playwright` through the same public API every application uses. There is no private SPI. TestBox and ColdBox use exactly what this page lists.

## The contract

| Surface | What you can rely on |
|---|---|
| `playwright()` BIF | Returns the manager. The first argument is a profile name, a list or array of profiles, or a settings struct; the second is a settings struct with the highest priority |
| Fluent objects | `Playwright`, `BrowserContext`, `Page`, `Locator`, `Expect`, `Request`, `PageObject`, `PageComponent`: every method in these docs |
| `bx:playwrightRender` | Renders its body to PDF, PNG, JPEG or WebP |
| Error types | The `Playwright.*` types in [Errors](errors.md) |
| Interception points | The events below, with the listed data keys |
| Module settings | The settings in [Configuration](configuration.md) and the `BX_PLAYWRIGHT_*` environment variables |

bx-playwright follows semantic versioning for everything in this table. A minor release only adds; removing or changing any of it waits for a major release and is listed in the changelog. Anything else (Java classes, methods not in these docs, file layouts under `home`) is internal and can change in any release.

## Detecting the module

Check for it before you use it, so your library still loads without it:

```js
function hasPlaywright() {
	return getModuleList().keyExists( "playwright" )
}
```

The module is registered as `playwright`, and its classes are available as `models.X@playwright`, for example, `new models.PageObject@playwright()`.

## Owning the lifecycle

`browse()` is the simplest contract for a test runner: it creates the pages, runs the callback, and closes everything even when the callback throws. When it throws, contexts close with `failed = true`, so failure artifacts are kept.

When the runner owns the lifecycle, for example one browser per test bundle and one context per test, use the manager and the context directly:

```js
// beforeAll
pw = playwright( [ "ci" ], { baseURL : "http://localhost:8080" } )

// each test
context = pw.newContext()
page    = context.newPage()
failed  = true
try {
	runTest( page )
	failed = false
} finally {
	kept = context.close( failed = failed )   // { screenshots, trace, videos, directory }
}

// afterAll
pw.close()
```

`context.close()` returns the artifacts it kept, ready to attach to the test result.

## Errors

Every error carries a `type`, a `message` and a `detail` with the fix. A runner maps them to its own outcomes:

| Type | Suggested outcome |
|---|---|
| `Playwright.AssertionFailed` | A test failure |
| `Playwright.Timeout`, `Playwright.ActionFailed` | A test error (the page did not behave) |
| `Playwright.NotInstalled` | Skip, or an error that tells the user to run `bxPlaywright install` |
| Any other `Playwright.*` | A test error |

TestBox counts only `TestBox.AssertionFailed` as a failure, so a TestBox integration translates `Playwright.AssertionFailed` and keeps the message and detail.

## Interception points

bx-playwright announces these events. Register a listener with `boxRegisterInterceptor()` or in a module. A listener that throws never breaks the browser flow.

| Event | Data |
|---|---|
| `onPlaywrightCreate` | `playwright` |
| `onBrowserLaunch` | `playwright`, `browser` |
| `onContextCreate` | `playwright`, `context` |
| `onPageCreate` | `page` |
| `onPageClose` | `page` |
| `onPlaywrightAssertionFailure` | `message`, `action` |
| `onPlaywrightArtifact` | `type` (`screenshot`, `pdf`, `trace`, `video`, `baseline`), `path`, and `page` for `page.screenshot()` and `page.pdf()` |

```js
boxRegisterInterceptor( ( data ) => {
	println( "Saved #data.type#: #data.path#" )
}, "onPlaywrightArtifact" )
```

A test runner can use `onPlaywrightArtifact` to collect every file a test produced, and `onPlaywrightAssertionFailure` to log failures that a soft assertion block collects.

## Configuration

Libraries pass settings as the second argument of `playwright()`, and users override them with profiles, module settings and environment variables. A library should not write module settings. Offer profiles instead, so users see them in `bxPlaywright profiles` and can change them.
