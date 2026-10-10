---
title: Configuration
order: 20
summary: Module settings, environment variables and resolution order.
tags: [reference]
---

# Configuration

Override any setting in `boxlang.json`:

```json
{
	"modules": {
		"playwright": {
			"settings": {
				"baseURL": "http://localhost:8080",
				"headless": false,
				"profiles": { "staging": { "extends": "desktop", "baseURL": "https://staging.example.com" } }
			}
		}
	}
}
```

| Setting | Default | Purpose |
|---|---|---|
| `home` | `~/.boxlang/playwright` | Driver, Node.js, browsers, sessions, artifacts |
| `browsersPath` | `{home}/browsers` | Browser installs |
| `nodePath` | | Explicit Node.js executable |
| `nodeVersion` | pinned per release | Node.js downloaded by `install` |
| `nodeDownloadURL` | `https://nodejs.org/dist` | Mirror for Node.js downloads |
| `defaultProfile` | `default` | Profile used by `playwright()` |
| `browser` | `chromium` | chromium, firefox, webkit |
| `channel` | | chrome, chrome-beta, msedge |
| `headless` | `true` | |
| `slowMo` | `0` | Milliseconds between actions |
| `baseURL` | | Relative URLs resolve against it |
| `viewport` | `{ width : 1280, height : 720 }` | |
| `device` | | A device name, see `bxPlaywright devices`. Its screen replaces the `viewport` setting and earlier viewports; a viewport set by a later profile or by options wins |
| `locale`, `timezone`, `colorScheme` | `colorScheme : light` | Emulation |
| `ignoreHTTPSErrors` | `false` | |
| `timeouts` | `{ action : 30000, navigation : 30000, assertion : 5000 }` | Milliseconds |
| `testIdAttribute` | `data-testid` | Used by `@name` and `byTestId()` |
| `artifacts` | all `off` | `{ directory, screenshot, trace, video }`, see [Testing](testing.md) |
| `snapshots` | `{ threshold : 0.2 }` | Visual regression, see [Quality Checks](quality.md). A relative `directory` resolves against the current directory |
| `render` | `{ format : A4, printBackground : true, waitUntil : networkidle }` | Rendering defaults |
| `launchOptions`, `contextOptions` | `{}` | Any Playwright launch or context option |
| `profiles` | `{}` | Your profiles, see [Profiles](profiles.md) |

## Environment variables

| Variable | Effect |
|---|---|
| `BX_PLAYWRIGHT_PROFILE` | Profile used when none is requested |
| `BX_PLAYWRIGHT_BROWSER`, `BX_PLAYWRIGHT_HEADLESS`, `BX_PLAYWRIGHT_BASEURL` | Override those settings |
| `BX_PLAYWRIGHT_UPDATE_SNAPSHOTS` | `true` rewrites visual baselines |
| `BX_PLAYWRIGHT_HOME` | The home directory |
| `PLAYWRIGHT_NODEJS_PATH` | Explicit Node.js executable |
| `PLAYWRIGHT_DOWNLOAD_CONNECTION_TIMEOUT` | Browser-download idle timeout in milliseconds; Playwright's default is `30000`. Each download is tried 5 times, so raise it only for a slow network |

## Resolution order

Last wins: module settings, profiles, environment variables, then the options passed to `playwright()`, `newContext()` or `newPage()`.

```js
playwright( "mobile", { locale : "es-ES" } )
pw.newPage( { colorScheme : "dark", timeouts : { assertion : 10000 } } )
```
