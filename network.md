---
title: Network and APIs
order: 12
summary: Mock requests, listen to traffic, test APIs, reuse logged-in sessions.
tags: [network, api]
---

# Network and APIs

Control what the browser sends and receives: mock or block requests, watch traffic, call HTTP APIs without a browser, and log in once for many tests.

```js
playwright().browse( ( page ) => {
	page.intercept( "**/api/users" ).respondJson( [ "Luis", "Brad" ] )
	page.visit( "http://localhost:8080/users" ).assertSee( "Brad" )
} )
```

## Mock and block requests

Register an intercept before the request happens, usually before `visit()`:

```js
page.intercept( "**/api/users" ).respondJson( [ "Luis", "Brad" ] )
page.intercept( "**/api/fail" ).respond( 500, "Boom" )
page.intercept( "**/*.{png,jpg}" ).abort()
page.intercept( "**/api/**" ).resume( { headers : { "X-Test" : "1" } } )
page.intercept( "**/api/**" ).handle( ( route ) => route.fallback() )
```

- Patterns are globs, full URLs or `page.regex()`.
- `respondJson( data, status, headers )` and `respond( status, body, headers )` answer the request; `abort()` blocks it; `resume( overrides )` lets it through, optionally changing `url`, `method`, `headers` or `postData`.
- `handle()` receives Playwright's `Route` for anything else: `fulfill()`, `abort()`, `resume()`, `fallback()`, `request()`.
- `context.intercept()` applies to every page of a context.

## Listen

```js
page.onConsole( ( message ) => println( message.type & ": " & message.text ) )
	.onRequest( ( sent ) => println( sent.method & " " & sent.url ) )
	.onResponse( ( received ) => println( received.status & " " & received.url ) )
	.onPageError( ( error ) => println( error.message ) )
	.onDialog( ( dialog ) => dialog.accept() )
```

| Listener | Receives |
|---|---|
| `onConsole` | `{ type, text, location }` |
| `onRequest` | `{ url, method, resourceType }` |
| `onResponse` | `{ url, status, ok }` |
| `onPageError` | `{ message, stack }` |
| `onDialog` | Playwright's `Dialog`: `accept()`, `accept( text )` or `dismiss()`. Without a listener, dialogs are dismissed |

!!! warning "Avoid scope names for arguments"
    On a web runtime, `request`, `url`, `form`, `cookie`, `session` and `cgi` resolve to BoxLang scopes, even as closure arguments. Name them `sent`, `received`, `address` and so on.

## API testing

```js
api = playwright().request( { baseURL : "http://localhost:8080" } )
try {
	login = api.post( "/api/login", { json : { user : "luis" }, headers : { "X-Token" : "abc" } } )
	api.expect( login ).toBeOK()

	users = api.get( "/api/users", { params : { page : 2 } } ).json()
} finally {
	api.close()
}
```

- Methods: `get()`, `post()`, `put()`, `patch()`, `delete()`.
- Options: `json`, `form`, `data`, `params`, `headers`, `timeout`, `failOnStatusCode`, `maxRedirects`.
- Responses have `status()`, `ok()`, `url()`, `headers()`, `text()` and `json()`.
- `context.request()` shares the cookies of a browser context: log in through the API, then browse.

## Cookies, permissions and offline

The context of a page holds its cookies and browser state:

```js
context = page.context()
context.addCookies( [ { name : "consent", value : "yes", url : "http://localhost:8080" } ] )
context.grantPermissions( [ "geolocation", "clipboard-read" ] )
context.setOffline( true )
println( context.cookies() )
```

Also `clearCookies()` and `saveStorageState( path )`.

## Saved sessions

Log in once, reuse the cookies and local storage everywhere:

```js
pw = playwright( { baseURL : "http://localhost:8080" } )

pw.session( "admin", ( page ) => {
	page.visit( "/login" )
		.fill( "Email", "admin@site.com" )
		.fill( "Password", "secret" )
		.click( "Sign in" )
		.assertPathIs( "/dashboard" )
}, { maxAge : 720 } )

pw.browse( ( page ) => {
	page.visit( "/admin" ).assertSee( "Dashboard" )
}, { session : "admin" } )
```

- The setup runs only when the session is missing, older than `maxAge` minutes (`0`, the default, never expires) or `refresh : true` is passed. `context` passes context overrides to the setup page.
- Use a session with `{ session : "admin" }` in `browse()`, `newPage()`, `newContext()`, `playwright()` or a [profile](profiles.md).
- The setup page always starts clean, even on a manager configured with `session`.
- Sessions are stored in `{home}/sessions`, outside your project, because they hold cookies.
