<p align="center">
	<picture>
		<source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/ortus-boxlang/bx-playwright/development/docs/assets/brand/bxplaywright-logo-horizontal-dark.svg">
		<img src="https://raw.githubusercontent.com/ortus-boxlang/bx-playwright/development/docs/assets/brand/bxplaywright-logo-horizontal.svg" alt="BxPlaywright" width="560">
	</picture>
</p>

# ⚡︎ BoxLang Playwright

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

Fluent browser automation and testing for BoxLang, powered by [Microsoft Playwright](https://playwright.dev). Drive Chromium, Firefox and WebKit, test web apps, mock the network, test APIs, and render HTML to PDF or images.

**Documentation:** [bxplaywright.boxlang.io](https://bxplaywright.boxlang.io) (also as [llms.txt](https://bxplaywright.boxlang.io/llms.txt) for AI agents)

## Install

Two distributions, same module (`playwright`), same API:

| Module | Size | Node.js |
|---|---|---|
| `bx-playwright` | ~4 MB | Downloaded by `bxPlaywright install` for your OS |
| `bx-playwright-full` | ~206 MB | Bundled for every platform (offline friendly) |

```bash
install-bx-module bx-playwright
bxPlaywright install            # driver + Node.js + Chromium
bxPlaywright install firefox webkit
bxPlaywright doctor             # check everything
```

Requires BoxLang 1.17+ and Java 21+.

## Quick Start

```js
playwright().visit( "https://boxlang.io" )
	.assertTitleContains( "BoxLang" )
	.click( "Docs" )
	.screenshot( "docs.png" )
	.quit()
```

```js
// Scoped work, cleaned up automatically
playwright( "mobile" ).browse( ( page ) => {
	page.visit( "http://localhost:8080/login" )
		.fill( "Email", "luis@ortus.com" )     // by label, placeholder or name
		.fill( "Password", "secret" )
		.click( "Sign in" )                    // by button or link text
		.assertPathIs( "/dashboard" )
		.assertSee( "Welcome" )
} )
```

```js
// One-shot helpers
playwright().screenshot( "https://boxlang.io", "home.png", { fullPage : true } )
playwright().pdf( "https://boxlang.io", "home.pdf", { format : "A4" } )
html = playwright().content( "https://boxlang.io" )     // rendered HTML
```

```html
<bx:playwrightRender type="pdf" path="invoice.pdf" format="A4" margin="1cm">
	<h1>Invoice #invoice.id#</h1>
</bx:playwrightRender>
```

## The API in One Screen

| Area | API |
|---|---|
| Entry | `playwright( [profile], [options] )`, `.visit()`, `.browse()`, `.newContext()`, `.newPage()`, `.request()`, `.render()`, `.close()` |
| Selectors | `@testId`, CSS / XPath (`#id`, `.class`, `h1`, `//div`), or visible text (labels for `fill`, buttons and links for `click`) |
| Actions | `click`, `dblclick`, `fill`, `type`, `clear`, `press`, `check`, `uncheck`, `select`, `upload`, `hover`, `focus`, `drag`, `scrollTo` |
| Finders | `locator`, `byRole`, `byText`, `byLabel`, `byPlaceholder`, `byTestId`, `byAltText`, `byTitle`, `frame`, `within` |
| Locators | `first`, `last`, `nth` (1-based), `filter`, `visible`, `all`, `count`, `texts` |
| Assertions | `assertSee`, `assertDontSee`, `assertTitle`, `assertPathIs`, `assertUrlIs`, `assertVisible`, `assertMissing`, `assertText`, `assertValue`, `assertChecked`, `assertCount`, ... |
| Expect | `page.expect( "h1" ).toHaveText( "Hi" )`, `.not().toBeVisible()`, `toHaveURL`, `toHaveCount`, `toMatchAriaSnapshot`, ... |
| Network | `intercept( "**/api/users" ).respondJson( data )`, `.respond()`, `.abort()`, `.resume()`, `.handle()` |
| Events | `onConsole`, `onPageError`, `onDialog`, `onRequest`, `onResponse`, `waitForPopup`, `waitForDownload` |
| Output | `screenshot`, `pdf`, `content`, `text`, `html`, `snapshot` (accessibility tree for AI agents) |

Assertions retry until they pass (web-first). Failures throw `Playwright.AssertionFailed` with Playwright's message. Other errors: `Playwright.Timeout`, `Playwright.ActionFailed`, `Playwright.InvalidOption`, `Playwright.InvalidProfile`, `Playwright.NotInstalled`.

## Profiles

`playwright( "mobile" )`, `playwright( [ "android", "dark" ] )`. Built-in: `default`, `chromium`, `firefox`, `webkit`, `chrome`, `chrome-beta`, `edge`, `hd`, `laptop`, `macbook`, `desktop`, `4k`, `mobile`/`iphone`, `iphone-se`, `mobile-landscape`, `android`/`pixel`, `galaxy`, `tablet`/`ipad`, `android-tablet`, `dark`, `light`, `reduced-motion`, `high-contrast`, `headed`, `debug`, `record`, `ci`, `offline`, `print`, `screenshot`.

Add your own in the module settings, extending any profile:

```json
"modules": {
	"playwright": {
		"settings": {
			"baseURL": "http://localhost:8080",
			"profiles": { "staging": { "extends": "desktop", "baseURL": "https://staging.example.com" } }
		}
	}
}
```

Resolution order (last wins): module settings, profiles, `BX_PLAYWRIGHT_*` environment variables (`BROWSER`, `HEADLESS`, `BASEURL`; `PROFILE` picks the default profile), per-call options.

## CLI

`bxPlaywright <verb>` (or `boxlang module:playwright <verb>`): `install`, `install-node`, `install-deps`, `uninstall`, `doctor`, `version`, `devices`, `profiles`, `clean`, `codegen`, `open`, `screenshot`, `pdf`, `show-trace`, `mcp`, `run`, `completions`, `help`. Add `--json` for machine readable output. Bash completions are installed with the module.

## Development

```bash
./gradlew downloadBoxLang
./gradlew shadowJar test                          # unit and integration tests
PLAYWRIGHT_E2E=true ./gradlew shadowJar test      # plus real browser tests (downloads Node.js and Chromium once)
./gradlew shadowJar -Pflavor=full                 # build bx-playwright-full
./gradlew spotlessApply                           # Ortus formatting
```

See [AGENTS.md](AGENTS.md) for the architecture and conventions, and [PLAN.md](PLAN.md) for the roadmap.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

> "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
