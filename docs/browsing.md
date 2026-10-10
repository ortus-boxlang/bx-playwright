---
title: Browsing
order: 10
summary: Visit pages, find elements with smart selectors, act on them.
tags: [dsl]
---

# Browsing

Open pages, find elements and act on them. Every action waits for its element, and every action returns the page, so calls chain.

```js
playwright( { baseURL : "http://localhost:8080" } ).browse( ( page ) => {
	page.visit( "/login" )
		.fill( "Email", "luis@ortus.com" )     // by label, placeholder or name
		.fill( "Password", "secret" )
		.check( "Remember me" )
		.select( "@role", "editor" )           // @testId
		.click( "Sign in" )                    // by button or link text
		.assertPathIs( "/dashboard" )
} )
```

## Smart selectors

One string finds elements:

| Selector | Finds |
|---|---|
| Visible text | `fill()` looks for a label, placeholder or name; `click()` for a button or link |
| `@name` | `data-testid="name"` (see the `testIdAttribute` setting), or a [page object](page-objects.md) alias |
| `#id`, `.class`, `input[name=email]`, `ul li`, `h1` | CSS (lowercase tag names) |
| `//div`, `xpath=...`, `css=...`, `text=...`, `role=button[name="Save"]` | Playwright selectors |
| `ref=e12` | An element ref from an [AI snapshot](ai.md) |

Explicit finders return a [locator](#locators): `locator()`, `byRole( "button", { name : "Save" } )`, `byText()`, `byLabel()`, `byPlaceholder()`, `byTestId()`, `byAltText()`, `byTitle()`, `frame( "iframe.preview" )`.

!!! tip "Id selectors in BoxLang strings"
    `#` starts interpolation in a BoxLang string, so write an id selector as `"##email"`, or use `"input[name=email]"` or a test id.

## Actions

| Method | Does |
|---|---|
| `visit( url, options )` | Navigate (relative to `baseURL`), or visit a [page object](page-objects.md) |
| `click( sel )`, `dblclick( sel )`, `hover( sel )`, `focus( sel )` | Mouse and focus |
| `fill( sel, value )`, `type( sel, text )`, `clear( sel )` | Inputs |
| `press( key )`, `press( sel, key )` | Keys: `Enter`, `Tab`, `Control+A`, `ControlOrMeta+S` |
| `check( sel )`, `uncheck( sel )`, `select( sel, values )`, `upload( sel, files )` | Forms |
| `drag( from, to )`, `scrollTo( sel )` | Other |
| `back()`, `forward()`, `reload()`, `setContent( html )` | Navigation |
| `within( sel, callback )` | Scope actions to an element |

On a locator, actions without a selector act on the locator: `page.byLabel( "Email" ).fill( "a@b.com" )`, `page.byLabel( "Resume" ).upload( "cv.pdf" )`.

## Locators

```js
todos = page.locator( ".todos li" )
todos.count()                                  // 3
todos.texts()                                  // [ "Write specs", ... ] (visible elements only)
todos.first().text()
todos.nth( 2 ).text()                          // 1-based: nth( 0 ) throws
todos.filter( { hasText : "Ship" } ).click()
todos.filter( { has : page.locator( ".done" ) } ).count()
todos.all().each( ( todo ) => println( todo.text() ) )
```

Also `last()`, `visible()`, `isChecked()` and `isEnabled()`. `filter()` takes `hasText`, `hasNotText`, `has`, `hasNot` and `visible`.

## Reading

`url()`, `title()`, `content()`, `text( sel )`, `html( sel )`, `value( sel )`, `attribute( sel, name )`, `count( sel )` (every match), `isVisible( sel )` (true when any match is visible), `evaluate( js, arg )`, `snapshot()`.

## Waiting

Actions and [assertions](assertions.md) already wait, so you rarely need these:

`waitFor( sel, state )`, `waitForText( text )`, `waitForUrl( url )`, `waitForLoadState( state )`, `wait( ms )`.

## Popups and downloads

```js
popup    = page.waitForPopup( () => page.click( "Open preview" ) )
download = page.waitForDownload( () => page.click( "Export CSV" ), "exports/orders.csv" )
println( download.path )     // also suggestedFilename and url
```

Dialogs (`alert`, `confirm`, `prompt`) are dismissed unless you listen with `onDialog()`, see [Network and APIs](network.md#listen).

## Viewport, media and time

```js
page.setViewport( 375, 812 )
page.emulate( { media : "print", colorScheme : "dark" } )
page.freezeTime( "2026-01-01T09:00:00" )     // Date.now() is fixed, timers keep running
```

`page.clock()` returns Playwright's clock for full control of timers. To start a page with a device, locale or color scheme, use a [profile](profiles.md) or [settings](configuration.md).

## Discover the API

Every object describes itself from its docblocks: `page.help()`, `page.help( "fill" )`, `playwright().help()`.
