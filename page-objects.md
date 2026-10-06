---
title: Page Objects, Components and Macros
order: 14
summary: Keep selectors and flows in classes, reuse widgets, add your own methods.
tags: [dsl, patterns]
---

# Page Objects, Components and Macros

## Page objects

```js
// pages/LoginPage.bx
class extends="models.PageObject@playwright" {
	url      = "/login"
	elements = { email : "#email", password : "input[type=password]", submit : "button" }

	function at() {
		page.assertTitle( "Login" )
	}

	function loginAs( required string email, string password = "secret" ) {
		page.fill( "@email", email ).fill( "@password", password ).click( "@submit" )
		return page.on( new pages.DashboardPage() )
	}
}
```

```js
home = page.visit( new pages.LoginPage() ).loginAs( "luis@ortus.com" )
home.assertSee( "Dashboard" )
```

- `page.visit( pageObject )` goes to its `url`, binds it and runs `at()`. `page.on( pageObject )` binds without navigating.
- `elements` become `@name` selectors. Inside the class, `page` is the bound page.
- Page methods work on the page object too and keep returning it.

## Components

```js
// pages/UserMenu.bx
class extends="models.PageComponent@playwright" {
	selector = "nav.user"
	elements = { logout : "a.logout" }

	function name() {
		return text( "span" )
	}
}
```

```js
page.within( new pages.UserMenu(), ( menu ) => menu.click( "@logout" ) )
menu = page.component( new pages.UserMenu() )
```

Every page action is available on a component, scoped to its root.

## Macros

```js
playwright().macro( "loginAs", ( page, email ) => page.visit( "/login" ).fill( "Email", email ).click( "Sign in" ) )
page.loginAs( "luis@ortus.com" ).assertSee( "Welcome" )

playwright().macro( "shout", ( locator ) => uCase( locator.text() ), "locator" )
```

Macros are global. Remove them with `removeMacro( name, target )`.
