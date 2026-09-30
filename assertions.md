---
title: Assertions
order: 11
summary: Web-first assertions in two styles, soft assertions and typed failures.
tags: [dsl, testing]
---

# Assertions

Assertions retry until they pass or the assertion timeout (5 s by default) expires. No sleeps needed.

## Inline style

```js
page.assertTitle( "Dashboard" )
	.assertSee( "Welcome" )
	.assertDontSee( "Error" )
	.assertPathIs( "/dashboard" )
	.assertVisible( "@menu" )
	.assertCount( ".todos li", 3 )
	.assertValue( "Email", "luis@ortus.com" )
```

| Method | Checks |
|---|---|
| `assertSee( text )`, `assertDontSee( text )` | Visible text in the page (or scope) |
| `assertTitle( t )`, `assertTitleContains( t )` | Title |
| `assertUrlIs( url )`, `assertPathIs( path )`, `assertUrlContains( t )` | URL |
| `assertVisible( sel )`, `assertMissing( sel )` | Visibility |
| `assertText( sel, t )`, `assertValue( sel, v )`, `assertAttribute( sel, name, v )` | Content |
| `assertChecked( sel )`, `assertNotChecked( sel )`, `assertEnabled( sel )`, `assertDisabled( sel )` | State |
| `assertCount( sel, n )` | Number of elements |

## Expect style

```js
page.expect( "h1" ).toHaveText( "Welcome" )
page.expect().toHaveTitle( "Dashboard" ).toHaveURL( page.regex( "/dashboard$" ) )
page.expect( "@error" ).not().toBeVisible()
```

`toBeVisible`, `toBeHidden`, `toBeEnabled`, `toBeDisabled`, `toBeChecked`, `toBeEditable`, `toBeEmpty`, `toBeFocused`, `toBeAttached`, `toBeInViewport`, `toHaveText`, `toContainText`, `toHaveValue`, `toHaveCount`, `toHaveAttribute`, `toHaveClass`, `toContainClass`, `toHaveId`, `toHaveCSS`, `toHaveAccessibleName`, `toHaveRole`, `toMatchAriaSnapshot`, `toHaveTitle`, `toHaveURL`, `toBeOK` (API responses). `not()` negates the next one.

## Soft assertions

Run everything, fail once with all the failures:

```js
page.soft( ( p ) => {
	p.assertTitle( "Store" ).assertSee( "Buy" ).assertVisible( "h1" )
} )
```

## Failures

A failure throws `Playwright.AssertionFailed` with Playwright's message: expected value, received value and the call log. Change the timeout with the `timeouts.assertion` setting or per page: `pw.newPage( { timeouts : { assertion : 10000 } } )`.
