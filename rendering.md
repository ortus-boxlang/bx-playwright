---
title: Screenshots, PDFs and Rendering
order: 13
summary: Screenshots, PDFs, rendered HTML, and the bx:playwrightRender component.
tags: [pdf, screenshots]
---

# Screenshots, PDFs and Rendering

Capture pages as images or PDFs, and render your own HTML with a real browser (modern CSS, web fonts, JavaScript). PDFs need Chromium, the default browser; the `print` profile is a good fit.

## One-shot helpers

No page to manage: each call opens a browser, captures and closes it.

```js
pw = playwright()
pw.screenshot( "https://boxlang.io", "home.png", { fullPage : true } )
pw.pdf( "https://boxlang.io", "home.pdf", { format : "A4" } )
html  = pw.content( "https://boxlang.io" )                 // the HTML after JavaScript ran
bytes = pw.render( "<h1>Hi</h1>", { type : "png" } )       // no path returns the bytes
```

`screenshot()`, `pdf()` and `content()` also accept `waitUntil` (`load`, `domcontentloaded`, `networkidle`).

## From a page

```js
page.screenshot( "page.png", { fullPage : true } )
page.locator( "@chart" ).screenshot( "chart.png" )
page.pdf( "page.pdf", { format : "Letter", landscape : true, margin : { top : "1cm" } } )
```

Both return the path, or the bytes when you pass no path. Screenshot options: `fullPage`, `type`, `quality`, `clip`, `omitBackground`, `animations`, `mask` (selectors to paint over). PDF options are listed below.

## bx:playwrightRender

The component renders its body to PDF, PNG, JPEG or WebP. In a template:

```html
<bx:playwrightRender type="pdf" path="invoice.pdf" format="A4" margin="1cm">
	<bx:output>
		<h1>Invoice #invoice.id#</h1>
	</bx:output>
</bx:playwrightRender>
```

In script:

```js
bx:playwrightRender type="png" variable="socialCard" viewport="1200x630" {
	writeOutput( socialCardHtml )
}
```

`render()` takes the same options as a struct, for example `pw.render( html, { type : "pdf", path : "invoice.pdf", margin : { top : "1cm" } } )`.

| Attribute | Meaning |
|---|---|
| `type` | `pdf` (default), `png`, `jpeg`, `webp` |
| `path` / `variable` | Write a file and/or store the result (at least one) |
| `baseURL` | Resolves relative links and assets in the body (a `<base href>` is added unless the body has one) |
| `waitFor`, `waitUntil` | Wait for a selector, or `load` / `networkidle` (default) |
| `format`, `landscape`, `margin`, `printBackground`, `scale`, `pageRanges`, `width`, `height`, `preferCSSPageSize` | PDF layout. `margin` is one size for every side, or a `{ top, right, bottom, left }` struct |
| `displayHeaderFooter`, `headerTemplate`, `footerTemplate` | PDF header and footer |
| `viewport` (`WxH`, or a `{ width, height }` struct), `fullPage`, `omitBackground`, `quality`, `device` | Image options |
| `colorScheme`, `locale`, `timezone` | Emulation |
| `profile`, `options` | A [profile](profiles.md), and a struct of any other `render()` option |

Defaults come from the `render` setting: `{ format : "A4", printBackground : true, waitUntil : "networkidle" }`.
