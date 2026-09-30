# Examples

Runnable, self-contained bx-playwright examples. They need no server or internet: pages are rendered
with `setContent()` or served through request interception. CI runs every example
(`PLAYWRIGHT_E2E=true ./gradlew test --tests '*ExamplesE2ETest'`), so the docs and AI skills that copy
from them stay correct.

| File | Shows |
|---|---|
| `render-pdf-and-images.bxs` | `render()`, screenshots and PDFs from HTML |
| `login-flow.bxs` | smart selectors, actions, inline assertions, network mocking |
| `page-objects.bxs` | page objects and components (`pages/`) |
| `quality-checks.bxs` | console errors, accessibility, visual regression, soft assertions |
| `ai-agent-tools.bxs` | the AI browser: snapshots with refs and tool definitions |

Run one with the BoxLang CLI from this folder: `boxlang login-flow.bxs`
