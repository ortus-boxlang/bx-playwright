---
title: Screenshots, PDFs and Rendering
order: 13
summary: Screenshots, PDFs, rendered HTML, and the bx:playwrightRender component.
tags: [pdf, screenshots]
---

# Screenshots, PDFs and Rendering

## One-shot helpers

```js
playwright().screenshot( "https://boxlang.io", "home.png", { fullPage : true } )
playwright().pdf( "https://boxlang.io", "home.pdf", { format : "A4" } )
html  = playwright().content( "https://boxlang.io" )            // HTML after JavaScript ran
bytes = playwright().render( "<h1>Hi</h1>", { type : "png" } )  // no path returns the bytes
```

## From a page

```js
page.screenshot( "page.png", { fullPage : true } )
page.locator( "@chart" ).screenshot( "chart.png" )
page.pdf( "page.pdf", { format : "Letter", landscape : true, margin : { top : "1cm" } } )
```

PDFs need Chromium (the `print` profile is a good fit).

## bx:playwrightRender

Render the body with a real browser (modern CSS, web fonts, JavaScript):

```html
<bx:playwrightRender type="pdf" path="invoice.pdf" format="A4" margin="1cm">
	<h1>Invoice #invoice.id#</h1>
</bx:playwrightRender>
```

```js
bx:playwrightRender type="png" variable="card" viewport="1200x630" {
	writeOutput( socialCardHtml )
}
```

| Attribute | Meaning |
|---|---|
| `type` | `pdf` (default), `png`, `jpeg`, `webp` |
| `path` / `variable` | Write a file and/or store the result (at least one) |
| `baseURL` | Resolves relative assets in the body |
| `waitFor`, `waitUntil` | Wait for a selector, or `load` / `networkidle` |
| `format`, `landscape`, `margin`, `printBackground`, `headerTemplate`, `footerTemplate`, `scale`, `pageRanges` | PDF options |
| `viewport` (`WxH`), `fullPage`, `omitBackground`, `quality`, `device` | Image options |
| `profile`, `options` | A profile, and any extra render() option |

Defaults come from the `render` setting.
