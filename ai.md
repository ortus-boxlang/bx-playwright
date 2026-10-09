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

`bxPlaywright mcp` starts [Playwright's MCP server](https://github.com/microsoft/playwright-mcp), so Claude, Cursor, VS Code and any other MCP client can drive a browser. It runs the driver that bx-playwright manages, with the configured browser (`--browser=chromium` by default, unless you pass `--browser`), so there is no Node.js project to install.

By default it talks over standard input and output: register the command with your client.

```bash
# Claude Code
claude mcp add playwright -- bxPlaywright mcp --headless
```

```json
{
  "mcpServers": {
    "playwright": {
      "command": "bxPlaywright",
      "args": [ "mcp", "--headless" ]
    }
  }
}
```

The JSON form works for Claude Desktop, Cursor and most clients. VS Code reads the same entry under a `servers` key in `.vscode/mcp.json`.

To share one server, start it on a port and point clients at its URL:

```bash
bxPlaywright mcp --headless --port 8931
# clients connect to http://localhost:8931/mcp (or /sse for the legacy transport)
```

Useful options, passed straight to Playwright: `--headless` (the browser is headed by default), `--browser`, `--port`, `--isolated` (keep the browser profile in memory), `--device "iPhone 15"`, `--viewport-size 1280x720` and `--output-dir`. Run `bxPlaywright mcp --help` for the full list.

## Codegen to BoxLang

`bxPlaywright codegen http://localhost:8080 --output=login.bxs` records your actions and writes bx-playwright code. `--target=java` (or javascript, python) keeps Playwright's own output.

## Self description

`page.help()`, `page.help( "fill" )` and `bxPlaywright help --json` describe the API for agents and humans. Errors always carry a `type` and a `detail` with the fix.
