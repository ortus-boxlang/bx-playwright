---
title: Quality Checks
order: 15
summary: Console errors, smoke tests, accessibility and visual regression.
tags: [testing, accessibility]
---

# Quality Checks

One-line checks for problems users notice: JavaScript errors, broken pages, accessibility issues and visual changes. Each one is an assertion, so it chains and fails with `Playwright.AssertionFailed`.

```js
page.assertNoConsoleErrors()
	.assertNoSmoke( [ "/", "/about", "/pricing" ] )
	.assertNoAccessibilityIssues( { impact : "serious" } )
	.assertScreenshotMatches( "home" )
```

## Console errors and smoke tests

- `consoleErrors()` returns the `console.error()` messages and uncaught exceptions of the page.
- `assertNoConsoleErrors( ignore )` fails if there are any. `ignore` is an array of substrings, such as `[ "favicon.ico" ]`.
- `assertNoSmoke( urls, { ignore, waitUntil } )` visits every URL and fails on HTTP 400+ or JavaScript errors, listing every problem at once.

## Accessibility

Audits run with axe-core, bundled with the module.

```js
violations = page.accessibility( { tags : [ "wcag2a", "wcag2aa" ] } )
page.assertNoAccessibilityIssues( { exclude : [ "color-contrast" ], impact : "moderate" } )
```

| Option | Meaning |
|---|---|
| `tags` | Rule sets to run, such as `wcag2a`, `wcag2aa` |
| `exclude` | Rule ids to skip |
| `include` | A CSS selector to audit instead of the whole page |
| `impact` | The minimum impact that fails: `minor` (default, everything), `moderate`, `serious`, `critical` |

`accessibility()` returns the violations: `{ id, impact, description, help, helpUrl, nodes }`.

## Visual regression

```js
page.assertScreenshotMatches( "dashboard", { fullPage : true, mask : [ "@clock" ] } )
page.locator( "@chart" ).assertScreenshotMatches( "sales-chart", { maxDiffPixelRatio : 0.01 } )
```

- The first run writes the baseline: `tests/snapshots/<name>.png` by default, see the `snapshots` setting.
- A mismatch fails and writes `<name>-actual.png` and `<name>-diff.png` (differences in red) next to it.
- Update baselines with `BX_PLAYWRIGHT_UPDATE_SNAPSHOTS=true` or `{ update : true }`.
- Animations are disabled and the caret hidden; the image is taken once it stops changing.
- Use the `screenshot` [profile](profiles.md) for stable images across machines.

| Option | Meaning |
|---|---|
| `threshold` | Per pixel color tolerance, 0 to 1 (default `0.2`) |
| `maxDiffPixels`, `maxDiffPixelRatio` | How many pixels, or which share of them, may differ (default `0`) |
| `mask` | Selectors or locators painted over before comparing |
| `fullPage`, `clip`, `omitBackground` | Screenshot options |
| `directory`, `update` | Where baselines live, and overwrite them |
