---
title: AI Agents
order: 17
summary: Snapshots with element refs, browser tools for bx-ai, MCP, codegen and help().
tags: [ai]
---

# AI Agents

## Snapshots with refs

```js
println( page.snapshot( options = { mode : "ai" } ) )
// - textbox "Email" [ref=e5]
// - button "Sign in" [ref=e7]
page.fill( "ref=e5", "luis@ortus.com" ).click( "ref=e7" )
```

The accessibility tree is compact and token efficient. Refs point at elements precisely.

## Browser tools for bx-ai

```js
agent = aiAgent(
	name         : "Browser",
	instructions : "Use the browser tools to complete the task.",
	tools        : playwright( { baseURL : "http://localhost:8080" } ).aiTools()
)
agent.run( "Log in as luis@ortus.com and tell me how many todos I have" )
```

Tools: `browser_visit`, `browser_snapshot`, `browser_click`, `browser_fill`, `browser_select`, `browser_press`, `browser_back`, `browser_text`, `browser_screenshot`, `browser_close`. Each returns the new page state; errors come back as text so the agent can recover.

For other frameworks, `aiToolDefinitions()` returns `[ { name, description, arguments, handler } ]`, and `models.AiBrowser@playwright` can be used directly.

## Playwright MCP

`bxPlaywright mcp` starts Playwright's MCP server for Claude, Cursor and other MCP clients.

## Codegen to BoxLang

`bxPlaywright codegen http://localhost:8080 --output=login.bxs` records your actions and writes bx-playwright code. `--target=java` (or javascript, python) keeps Playwright's own output.

## Self description

`page.help()`, `page.help( "fill" )` and `bxPlaywright help --json` describe the API for agents and humans. Errors always carry a `type` and a `detail` with the fix.
