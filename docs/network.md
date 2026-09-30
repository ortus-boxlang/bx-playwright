---
title: Network and APIs
order: 12
summary: Mock requests, listen to traffic, test APIs, reuse logged-in sessions.
tags: [network, api]
---

# Network and APIs

## Mock and block requests

```js
page.intercept( "**/api/users" ).respondJson( [ "Luis", "Brad" ] )
page.intercept( "**/api/fail" ).respond( 500, "Boom" )
page.intercept( "**/*.{png,jpg}" ).abort()
page.intercept( "**/api/**" ).resume( { headers : { "X-Test" : "1" } } )
page.intercept( "**/api/**" ).handle( ( route ) => route.fallback() )
```

`context.intercept()` applies to every page of a context. Patterns are globs, full URLs or `page.regex()`.

## Listen

```js
page.onConsole( ( message ) => println( message.type & ": " & message.text ) )
	.onRequest( ( request ) => println( request.method & " " & request.url ) )
	.onResponse( ( response ) => println( response.status & " " & response.url ) )
	.onPageError( ( error ) => println( error.message ) )
	.onDialog( ( dialog ) => dialog.accept() )
```

## API testing

```js
api = playwright().request( { baseURL : "http://localhost:8080" } )
response = api.post( "/api/login", { json : { user : "luis" }, headers : { "X-Token" : "abc" } } )
api.expect( response ).toBeOK()
users = api.get( "/api/users", { params : { page : 2 } } ).json()
api.close()
```

Options: `json`, `form`, `data`, `params`, `headers`, `timeout`, `failOnStatusCode`, `maxRedirects`. Responses have `status()`, `ok()`, `headers()`, `text()`, `json()`. `context.request()` shares the context cookies.

## Saved sessions

Log in once, reuse the cookies everywhere:

```js
pw = playwright( { baseURL : "http://localhost:8080" } )
pw.session( "admin", ( page ) => {
	page.visit( "/login" ).fill( "Email", "admin@site.com" ).fill( "Password", "secret" ).click( "Sign in" )
} )
pw.newPage( { session : "admin" } ).visit( "/admin" ).assertSee( "Dashboard" )
```

Options: `maxAge` (minutes, 0 = never expires), `refresh`, `context`. Sessions are stored in `{home}/sessions`, outside your project, because they hold cookies.
