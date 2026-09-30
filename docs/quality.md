---
title: Quality Checks
order: 15
summary: Console errors, smoke tests, accessibility and visual regression.
tags: [testing, accessibility]
---

# Quality Checks

```js
page.assertNoConsoleErrors()
	.assertNoSmoke( [ "/", "/about", "/pricing" ] )
	.assertNoAccessibilityIssues( { impact : "serious" } )
	.assertScreenshotMatches( "home" )
```

## Console errors and smoke tests

- `consoleErrors()` returns `console.error()` messages and uncaught exceptions.
- `assertNoConsoleErrors( ignore = [] )` fails if there are any (ignore by substring).
- `assertNoSmoke( urls, { ignore, waitUntil } )` visits every URL and fails on HTTP 400+ or JavaScript errors, listing every problem at once.

## Accessibility

Powered by axe-core (bundled).

```js
violations = page.accessibility( { tags : [ "wcag2a", "wcag2aa" ] } )
page.assertNoAccessibilityIssues( { exclude : [ "color-contrast" ], impact : "moderate" } )
```

Options: `tags`, `exclude` (rule ids), `include` (a CSS selector to audit), `impact` (minimum that fails: minor, moderate, serious, critical).

## Visual regression

```js
page.assertScreenshotMatches( "dashboard", { fullPage : true, mask : [ "@clock" ] } )
page.locator( "@chart" ).assertScreenshotMatches( "sales-chart", { maxDiffPixelRatio : 0.01 } )
```

- The first run writes the baseline (`tests/snapshots/<name>.png` by default, see the `snapshots` setting).
- A mismatch fails and writes `<name>-actual.png` and `<name>-diff.png` (differences in red).
- Update baselines with `BX_PLAYWRIGHT_UPDATE_SNAPSHOTS=true` or `{ update : true }`.
- Options: `threshold` (color tolerance, 0.2), `maxDiffPixels`, `maxDiffPixelRatio`, `mask`, `fullPage`, `directory`.
- Use the `screenshot` profile for stable images across machines.
