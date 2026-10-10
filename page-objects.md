---
title: Page Objects, Components and Macros
order: 14
summary: Keep selectors and flows in classes, reuse widgets, add your own methods.
tags: [dsl, patterns]
---

# Page Objects, Components and Macros

When the same selectors and steps appear in many scripts or tests, move them into classes:

- A **page object** is one page: its URL, its element aliases and its flows.
- A **component** is a widget used on many pages, such as a menu or a date picker.
- A **macro** adds your own method to every page or locator.

## Page objects

```js
// pages/LoginPage.bx
class extends="models.PageObject@playwright" {

	url      = "/login"
	elements = {
		email    : "input[name=email]",
		password : "input[type=password]",
		submit   : "button[type=submit]"
	}

	function at() {
		page.assertTitle( "Login" )
	}

	function loginAs( required string email, string password = "secret" ) {
		page.fill( "@email", arguments.email )
			.fill( "@password", arguments.password )
			.click( "@submit" )
		return page.on( new pages.DashboardPage() )
	}

}
```

```js
playwright( { baseURL : "http://localhost:8080" } ).browse( ( page ) => {
	page.visit( new pages.LoginPage() )
		.loginAs( "luis@ortus.com" )
		.assertSee( "Dashboard" )
} )
```

- `page.visit( pageObject )` goes to its `url`, binds it and runs `at()`. `page.on( pageObject )` binds without navigating, so a flow can return the next page (`DashboardPage` is another page object).
- `elements` become `@name` selectors. `element( "email" )` returns the locator of an alias.
- Inside the class, `page` is the bound page.
- Page methods work on the page object too and keep returning it, so chains continue.

## Components

```js
// pages/UserMenu.bx
class extends="models.PageComponent@playwright" {

	selector = "nav.user"
	elements = { logout : "a.logout" }

	function userName() {
		return text( "span" )
	}

}
```

```js
page.within( new pages.UserMenu(), ( menu ) => menu.click( "@logout" ) )

menu = page.component( new pages.UserMenu() )
println( menu.userName() )
```

Every page action and assertion is available on a component, scoped to its `selector`.

## Macros

```js
pw = playwright( { baseURL : "http://localhost:8080" } )

pw.macro( "loginAs", ( page, email ) => {
	page.visit( "/login" ).fill( "Email", email ).click( "Sign in" )
} )
pw.macro( "shout", ( locator ) => uCase( locator.text() ), "locator" )

pw.browse( ( page ) => {
	page.loginAs( "luis@ortus.com" ).assertSee( "Welcome" )
	println( page.locator( "h1" ).shout() )
} )
```

- The handler receives the page (or locator) first, then the call arguments. Return nothing to keep chaining.
- The third argument is the target: `page` (default) or `locator`.
- Macros are global to the runtime: register them once, for example in `beforeAll()`. Remove them with `removeMacro( name, target )`, or every macro of a target with `removeMacro( target = "page" )`.
