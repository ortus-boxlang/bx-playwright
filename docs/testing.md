---
title: Testing
order: 16
summary: Use bx-playwright in tests, keep artifacts on failure, debug.
tags: [testing]
---

# Testing

bx-playwright works in any test framework. TestBox will ship base specs and matchers built on the same public API.

```js
describe( "Login", () => {
	it( "signs in", () => {
		playwright( "ci" ).browse( ( page ) => {
			page.visit( "http://localhost:8080/login" )
				.fill( "Email", "luis@ortus.com" )
				.fill( "Password", "secret" )
				.click( "Sign in" )
				.assertPathIs( "/dashboard" )
		} )
	} )
} )
```

`browse()` closes the context with `failed = true` when the callback throws, so failure artifacts are kept.

## Artifacts

The `artifacts` setting (or a profile such as `ci`, `record`, `debug`) records screenshots, traces and videos:

| Policy | Keeps |
|---|---|
| `off` | nothing |
| `on` | always |
| `only-on-failure` / `retain-on-failure` | only when the context closes with `failed = true` |

```js
context = pw.newContext( { artifacts : { trace : "retain-on-failure", screenshot : "only-on-failure" } } )
// ...
kept = context.close( failed = true )   // { screenshots, trace, videos, directory }
```

Open a trace with `bxPlaywright show-trace path/to/trace.zip`.

## Debugging

- `playwright( "debug" )`: headed, slowed down, every artifact on.
- `playwright( "headed" )` or `BX_PLAYWRIGHT_HEADLESS=false`.
- `page.snapshot()` prints the accessibility tree.
- `bxPlaywright codegen http://localhost:8080` records your clicks as BoxLang code.

## CI

```bash
install-bx-module bx-playwright
bxPlaywright install chromium --with-deps
BX_PLAYWRIGHT_PROFILE=ci boxlang run-tests.bxs
```

Cache `~/.boxlang/playwright` between runs to skip the downloads.
