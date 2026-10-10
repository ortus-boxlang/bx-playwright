---
title: Automation Scripts
order: 18
summary: Write, record, schedule and run browser automation jobs with BoxLang scripts.
tags: [automation, scripts]
---

# Automation Scripts

bx-playwright is not only for tests. Any BoxLang script can drive a browser: fill a form every morning, export a report from a site without an API, scrape a table into JSON, check that a page is up, or capture screenshots and PDFs on a schedule.

```js
// export.bxs
playwright( { baseURL : "https://app.example.com" } ).browse( ( page ) => {
	page.visit( "/login" )
		.fill( "Email", getSystemSetting( "APP_USER" ) )
		.fill( "Password", getSystemSetting( "APP_PASSWORD" ) )
		.click( "Sign in" )
		.assertPathIs( "/dashboard" )

	page.visit( "/reports" )
	page.waitForDownload( () => page.click( "Export CSV" ), "reports/orders.csv" )
} )
```

```bash
APP_USER=bot@example.com APP_PASSWORD=*** boxlang export.bxs
```

## Write a script

A script is a `.bxs` file. Wrap the work in `browse()`: it opens a fresh page, runs your code and closes the browser, also when the code throws.

- Selectors are the text people see: `fill( "Email", ... )` finds the field by its label, placeholder or name, and `click( "Sign in" )` finds the button or link. See [Browsing](browsing.md).
- Actions wait for elements on their own, and assertions such as `assertPathIs()` retry until they pass or time out. A step that fails stops the script with a clear error.
- Read secrets from environment variables with `getSystemSetting()`, never from the script.

### Arguments

`cliGetArgs()` returns the arguments of the script: `options` for `--name=value` flags and `positionals` for the rest.

```js
// capture.bxs
args    = cliGetArgs()
address = args.options.url ?: "https://boxlang.io"
output  = args.options.out ?: "capture.png"

playwright().screenshot( address, output, { fullPage : true } )
println( "Saved #output#" )
```

```bash
boxlang capture.bxs --url=https://ortussolutions.com --out=ortus.png
```

## Record a script with codegen

Do not write the clicks by hand: record them.

```bash
bxPlaywright codegen https://app.example.com --output=job.bxs
```

A browser opens with the Playwright inspector. Click through the job, close the browser, and `job.bxs` holds the steps as bx-playwright code. Then:

1. Look for `// TODO translate:` comments: steps codegen could not translate. Rewrite them with the [Browsing](browsing.md) API.
2. Replace typed passwords and other secrets with `getSystemSetting()`.
3. Wrap the steps in `browse()` if they are not, and add an assertion after each important step so a broken job fails where it breaks.

## Log in once with a saved session

Logging in on every run is slow, and some sites challenge frequent logins. A [saved session](network.md#saved-sessions) logs in once and reuses the cookies and local storage until it is `maxAge` minutes old. The [complete job](#a-complete-job) below uses one.

## Get data out

```js
playwright().browse( ( page ) => {
	page.visit( "https://app.example.com/orders" )

	// A table into an array of structs
	var orders = page.locator( "table.orders tbody tr" ).all().map( ( row ) => {
		var cells = row.locator( "td" ).texts()
		return { id : cells[ 1 ], customer : cells[ 2 ], total : cells[ 3 ] }
	} )
	fileWrite( "orders.json", jsonSerialize( orders ) )

	// Single values
	var headline = page.text( "h1" )
	var count    = page.count( ".order" )
} )
```

- `locator( sel ).texts()` returns the visible texts of every match; `text( sel )`, `value( sel )` and `attribute( sel, name )` read one element. See [Browsing](browsing.md#reading).
- Files: `page.screenshot( path )`, `page.pdf( path )` and `page.waitForDownload( () => page.click( "Export" ), path )`.
- Without a page: `playwright().screenshot( url, path )`, `pdf( url, path )`, `content( url )` (the HTML after JavaScript ran). See [Screenshots, PDFs and Rendering](rendering.md).

## Handle failures

Every error has a `type`, a message and a `detail` with the fix (see [Errors](errors.md)). Catch the ones a job can recover from, and retry the whole job for flaky sites:

```js
function runJob() {
	playwright( "ci" ).browse( ( page ) => {
		page.visit( "https://app.example.com/status" ).assertSee( "All systems operational" )
	} )
}

for ( attempt = 1; attempt <= 3; attempt++ ) {
	try {
		runJob()
		break
	} catch ( "Playwright.Timeout" e ) {
		if ( attempt == 3 ) {
			rethrow
		}
		println( "Attempt #attempt# timed out, retrying: #e.message#" )
		sleep( 5000 )
	}
}
```

The `ci` profile keeps a screenshot, a trace and a video of a failed `browse()`, so you can see what the page looked like when the job broke: open the trace with `bxPlaywright show-trace path/to/trace.zip`. Artifacts go to `{home}/artifacts`, or the `artifacts.directory` setting.

Schedulers and CI systems read the exit code. A script that throws exits with a non zero code; to fail on your own condition, call `cliExit( 1 )`, as the [complete job](#a-complete-job) does.

## Run it on a server

Scripts run headless by default. On a server or in a container:

```bash
# Once: Chromium plus the Linux libraries it needs
bxPlaywright install chromium --with-deps
bxPlaywright doctor

# Every run
boxlang /jobs/export.bxs
```

- Choose a behavior with a profile: `playwright( "ci" )` for headless runs that keep failure artifacts, `playwright( "debug" )` to watch the job in a headed, slowed down browser while you build it. Or set `BX_PLAYWRIGHT_PROFILE` without touching the script.
- `BX_PLAYWRIGHT_BASEURL`, `BX_PLAYWRIGHT_BROWSER` and `BX_PLAYWRIGHT_HEADLESS` override those settings per environment. See [Configuration](configuration.md#environment-variables).
- bx-playwright closes every browser it started when the script ends, even when it never called `close()`, so jobs do not leave browser processes behind. A process killed with `kill -9` cannot clean up.

## Schedule it

### cron

```cron
# Every weekday at 07:00, with a log
0 7 * * 1-5  cd /jobs && boxlang export.bxs >> /var/log/jobs/export.log 2>&1
```

### A BoxLang scheduler

To keep the schedule in BoxLang, write a scheduler class and run it with `boxlang schedule`:

```js
// schedulers/JobsScheduler.bx
class {

	property name="scheduler"

	function configure() {
		scheduler.setSchedulerName( "browser-jobs" )

		scheduler.task( "export-orders" )
			.call( () => {
				playwright( "ci" ).browse( ( page ) => {
					page.visit( "https://app.example.com/orders" )
					page.screenshot( "/jobs/out/orders-#dateFormat( now(), "yyyy-MM-dd" )#.png" )
				} )
			} )
			.everyHour()
	}

	function onAnyTaskError( task, exception ) {
		println( "Task [#task.getName()#] failed: #exception.getMessage()#" )
	}

}
```

```bash
boxlang schedule schedulers/JobsScheduler.bx
```

The scheduler runs until you stop it (Ctrl+C). To start it with the runtime, list it in the `schedulers` setting of `boxlang.json`. Inside a ColdBox application, use its scheduler and call the same code from a task. See the [BoxLang scheduled tasks guide](https://boxlang.ortusbooks.com/boxlang-framework/asynchronous-programming/scheduled-tasks).

- Each run starts its own browser inside `browse()`. Playwright is not thread safe: never share a page or a manager between tasks that run at the same time.
- Use absolute paths for files a scheduled task writes: relative paths do not resolve against the directory you started the scheduler from.
- Date masks are case sensitive: `yyyy-MM-dd` is the date, `mm` is minutes.

## A complete job

```js
// jobs/daily-orders.bxs: export yesterday's orders to JSON, with a screenshot of the page
pw = playwright( "ci", { baseURL : getSystemSetting( "APP_URL" ) } )

pw.session( "app", ( page ) => {
	page.visit( "/login" )
		.fill( "Email", getSystemSetting( "APP_USER" ) )
		.fill( "Password", getSystemSetting( "APP_PASSWORD" ) )
		.click( "Sign in" )
		.assertPathIs( "/dashboard" )
}, { maxAge : 720 } )

orders = pw.browse( ( page ) => {
	page.visit( "/orders?range=yesterday" ).assertSee( "Orders" )
	page.screenshot( "out/orders.png", { fullPage : true } )
	return page.locator( "table.orders tbody tr" ).all().map( ( row ) => {
		var cells = row.locator( "td" ).texts()
		return { id : cells[ 1 ], customer : cells[ 2 ], total : cells[ 3 ] }
	} )
}, { session : "app" } )

if ( !orders.len() ) {
	println( "No orders found" )
	cliExit( 1 )
}

fileWrite( "out/orders.json", jsonSerialize( orders ) )
println( "Exported #orders.len()# orders" )
```

```bash
APP_URL=https://app.example.com APP_USER=bot@example.com APP_PASSWORD=*** boxlang jobs/daily-orders.bxs
```

## Let an AI agent do it

When the steps change too often to script, let an agent drive the browser instead: `playwright().aiTools()` gives bx-ai agents browser tools, and `bxPlaywright mcp` serves them to Claude and other MCP clients. See [AI Agents](ai.md).
