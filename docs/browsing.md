---
title: Browsing
order: 10
summary: Visit pages, find elements with smart selectors, act on them.
tags: [dsl]
---

# Browsing

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
| `@name` | `data-testid="name"` (see the `testIdAttribute` setting), or a page object alias |
| `#id`, `.class`, `input[name=email]`, `ul li`, `h1` | CSS (lowercase tag names) |
| `//div`, `xpath=...`, `css=...`, `text=...`, `role=button[name="Save"]` | Playwright selectors |
| `ref=e12` | An element ref from an AI snapshot |
| Anything else | Visible text. `fill()` looks for a label, placeholder or name; `click()` for a button or link |

Explicit finders return a Locator: `locator()`, `byRole( "button", { name : "Save" } )`, `byText()`, `byLabel()`, `byPlaceholder()`, `byTestId()`, `byAltText()`, `byTitle()`, `frame( "#iframe" )`.

## Actions

All return the page (or locator), so they chain.

| Method | Does |
|---|---|
| `visit( url, options )` | Navigate (relative to `baseURL`), or visit a page object |
| `click( sel )`, `dblclick( sel )`, `hover( sel )`, `focus( sel )` | Mouse and focus |
| `fill( sel, value )`, `type( sel, text )`, `clear( sel )` | Inputs |
| `press( key )`, `press( sel, key )` | Keys: `Enter`, `Tab`, `Control+A`, `ControlOrMeta+S` |
| `check( sel )`, `uncheck( sel )`, `select( sel, values )`, `upload( sel, files )` | Forms |
| `drag( from, to )`, `scrollTo( sel )` | Other |
| `back()`, `forward()`, `reload()`, `setContent( html )` | Navigation |
| `wait( ms )`, `waitFor( sel, state )`, `waitForText( text )`, `waitForUrl( url )`, `waitForLoadState()` | Waiting (actions already auto-wait) |
| `waitForPopup( fn )`, `waitForDownload( fn, path )` | New tabs and downloads |
| `within( sel, fn )` | Scope actions to an element |

On a locator, actions without a selector act on the locator: `page.byLabel( "Email" ).fill( "a@b.com" )`.

## Locators

```js
todos = page.locator( ".todos li" )
todos.count()                  // 3
todos.texts()                  // [ "Write specs", ... ]
todos.nth( 2 ).text()          // 1-based
todos.filter( { hasText : "Ship" } ).click()
todos.all().each( ( item ) => println( item.text() ) )
```

## Reading

`url()`, `title()`, `content()`, `text( sel )`, `html( sel )`, `value( sel )`, `attribute( sel, name )`, `count( sel )`, `isVisible( sel )`, `evaluate( js, arg )`, `snapshot()`.

## Discover the API

Every object describes itself from its docblocks: `page.help()`, `page.help( "fill" )`, `playwright().help()`.
