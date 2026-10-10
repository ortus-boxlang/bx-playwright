---
title: Errors
order: 33
summary: Every error type, what it means and how to fix it.
tags: [reference]
---

# Errors

Every error has a `type` you can catch and a `detail` that explains the fix.

```js
try {
	page.click( "Checkout" )
} catch ( "Playwright.Timeout" e ) {
	println( e.message & " / " & e.detail )
}
```

| Type | When | Fix |
|---|---|---|
| `Playwright.AssertionFailed` | An assertion did not pass in time | Read expected/received in the message; raise `timeouts.assertion` if the page is slow |
| `Playwright.Timeout` | An action waited too long for an element | Check the selector (`page.snapshot()` helps); raise `timeouts.action` |
| `Playwright.ActionFailed` | Playwright refused an action (detached, not editable, navigation error, ...) | The message has Playwright's call log |
| `Playwright.InvalidOption` | Unknown option, bad value, unknown method or device | The detail lists the valid values |
| `Playwright.InvalidProfile` | Unknown profile or an `extends` loop | The detail lists the profiles |
| `Playwright.NotInstalled` | Driver, Node.js, browser or bx-ai missing | Run `bxPlaywright install` or `bxPlaywright doctor` |
| `Playwright.NodeInstallFailed` | Node.js download or checksum failed | Check the network, use `nodeDownloadURL` or `nodePath` |
| `Playwright.UnsupportedPlatform` | The operating system or CPU has no Playwright driver (only Linux, macOS and Windows on x64 or arm64), or no Node.js build (Windows ARM64) | Run on a supported platform; on Windows ARM64 set `nodePath` to a Node.js 20+ executable |
