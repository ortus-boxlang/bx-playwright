---
title: Profiles
order: 21
summary: Built-in and custom profiles for browsers, devices, screens and modes.
tags: [reference]
---

# Profiles

```js
playwright( "mobile" )
playwright( [ "android", "dark" ] )              // merged left to right
playwright( "tablet", { locale : "es-ES" } )     // plus overrides
```

| Group | Profiles |
|---|---|
| Browsers | `default`, `chromium`, `firefox`, `webkit`, `chrome`, `chrome-beta`, `edge` |
| Screens | `hd` (1280x720), `laptop` (1366x768), `macbook` (1440x900 @2x), `desktop` (1920x1080), `4k` |
| Devices | `mobile` / `iphone` (iPhone 15), `iphone-se`, `mobile-landscape`, `android` / `pixel` (Pixel 7), `galaxy` (Galaxy S24), `tablet` / `ipad` (iPad Pro 11), `android-tablet` (Galaxy Tab S9) |
| Appearance | `dark`, `light`, `reduced-motion`, `high-contrast` |
| Modes | `headed`, `debug`, `record`, `ci`, `offline`, `print`, `screenshot` |

See every value with `bxPlaywright profiles` or one resolved with `bxPlaywright profiles mobile`.

## Your own profiles

```json
"profiles": {
	"staging": { "extends": "desktop", "baseURL": "https://staging.example.com" },
	"admin-mobile": { "extends": [ "mobile", "dark" ], "session": "admin" }
}
```

A profile can hold any setting. `extends` takes one name or a list. A profile with a built-in name replaces it.
