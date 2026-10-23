---
title: AI Agents
order: 17
summary: Snapshots with element refs, browser tools for bx-ai, MCP, codegen and help().
tags: [ai]
---

# AI Agents

Let an AI agent drive a browser: compact page snapshots with element refs, ready-made browser tools for bx-ai and other frameworks, and Playwright's MCP server for Claude, Cursor and VS Code.

## Snapshots with refs

```js
println( page.snapshot( options = { mode : "ai" } ) )
// - textbox "Email" [ref=e5]
// - button "Sign in" [ref=e7]
page.fill( "ref=e5", "luis@ortus.com" ).click( "ref=e7" )
```

The accessibility tree is compact and token efficient. Every element gets a ref that works as a selector until the page changes. `snapshot( selector )` limits the tree to one element.

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

### Registered for you

When the bx-ai module is loaded, the same tools are also registered in bx-ai's tool registry as `browser_visit@bxplaywright`, `browser_click@bxplaywright` and so on. It does not matter which module loads first. An agent can pick them by name, or take them all:

```js
aiAgent( name : "Browser", tools : [ "browser_visit@bxplaywright", "browser_snapshot@bxplaywright" ] )
aiAgent( name : "Browser", tools : aiToolRegistry().getByModule( "bxplaywright" ) )
```

They use the module settings (`browser`, `headless`, `baseURL`, ...) and share one page. Nothing starts until a tool is used. Frameworks that give every agent every registered tool will give them the browser, so turn it off in `boxlang.json` if you only want `aiTools()`:

```json
"modules": { "bxplaywright": { "settings": { "registerAiTools": false } } }
```

`aiTools()` returns tools of its own and does not add them to the registry.

For other frameworks, `aiToolDefinitions()` returns `[ { name, description, arguments, handler } ]`, and `models.AiBrowser@bxplaywright` can be used directly.

## Claude Code and Codex plugins

The [BoxLang agent plugins](https://github.com/ortus-boxlang/skills) set up a coding agent in two installs, from the same marketplace:

| Plugin | Gives the agent |
|---|---|
| `boxlang-agent-skills` | The seven bx-playwright skills (setup, browsing, assertions, network, rendering, testing, AI), every other BoxLang skill, and the Ortus docs MCP servers |
| `bx-playwright` (opt-in) | A real browser through `bxplaywright mcp --headless` and, in Claude Code, the `/bx-playwright:doctor` and `/bx-playwright:new-browser-spec` commands |

```bash
# Claude Code
claude plugin marketplace add ortus-boxlang/skills
claude plugin install boxlang-agent-skills@boxlang-agent-skills
claude plugin install bx-playwright@boxlang-agent-skills

# Codex
codex plugin marketplace add ortus-boxlang/skills
codex plugin add boxlang-agent-skills@boxlang-agent-skills
codex plugin add bx-playwright@boxlang-agent-skills
```

The `bx-playwright` plugin starts `bxplaywright` from your `PATH`, so install the module and a browser first (`install-bx-module bx-playwright`, then `bxplaywright install`). It is a separate plugin because without the module its server fails to start.

For other agents, `npx skills add ortus-boxlang/skills/boxlang-modules/bx-playwright` installs the skills, and the next section registers the MCP server by hand.

## Playwright MCP

`bxplaywright mcp` starts [Playwright's MCP server](https://github.com/microsoft/playwright-mcp), so Claude, Cursor, VS Code and any other MCP client can drive a browser. It runs the driver that bx-playwright manages, with the configured browser (`--browser=chromium` by default, unless you pass `--browser`), so there is no Node.js project to install.

By default it talks over standard input and output: register the command with your client (the `bx-playwright` plugin above does this for Claude Code and Codex).

```bash
# Claude Code
claude mcp add playwright -- bxplaywright mcp --headless
```

```json
{
  "mcpServers": {
    "bxplaywright": {
      "command": "bxplaywright",
      "args": [ "mcp", "--headless" ]
    }
  }
}
```

The JSON form works for Claude Desktop, Cursor and most clients. VS Code reads the same entry under a `servers` key in `.vscode/mcp.json`.

To share one server, start it on a port and point clients at its URL:

```bash
bxplaywright mcp --headless --port 8931
# clients connect to http://localhost:8931/mcp (or /sse for the legacy transport)
```

Useful options, passed straight to Playwright: `--headless` (the browser is headed by default), `--browser`, `--port`, `--isolated` (keep the browser profile in memory), `--device "iPhone 15"`, `--viewport-size 1280x720` and `--output-dir`. Run `bxplaywright mcp --help` for the full list.

## Codegen to BoxLang

`bxplaywright codegen http://localhost:8080 --output=login.bxs` records your actions and writes bx-playwright code, see [Automation Scripts](automation.md#record-a-script-with-codegen). `--target=java` (or javascript, python) keeps Playwright's own output.

## Self description

`page.help()`, `page.help( "fill" )` and `bxplaywright help --json` describe the API for agents and humans. Errors always carry a `type` and a `detail` with the fix, see [Errors](errors.md).
