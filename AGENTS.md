# bx-playwright: Agent Guidelines

## What This Is

A BoxLang module (registered as `playwright`) wrapping Microsoft Playwright Java: a fluent DSL (`playwright()` BIF), a component (`bx:playwrightRender`) and a CLI (`bxPlaywright`). Published as two ForgeBox modules from one source tree: `bx-playwright` (small, downloads Node.js) and `bx-playwright-full` (bundles Node.js). See `PLAN.md` for the design and roadmap.

## Layout

- `src/main/bx/ModuleConfig.bx`: settings (every setting documented inline), interception points, CLI `main()`/`dispatch()`
- `src/main/bx/bifs/Playwright.bx`: the `playwright()` BIF
- `src/main/bx/models/`: the DSL
  - `Config.bx` (settings + profiles + env resolution), `Profiles.bx` (built-in profiles, extends, deep merge)
  - `Playwright.bx` (manager), `BrowserContext.bx` (artifacts), `Page.bx`, `Locator.bx`, `Scope.bx` (shared actions/assertions), `Expect.bx`, `Route.bx`, `Request.bx`, `Response.bx`, `Base.bx` (helpers, typed errors)
  - `cli/`: `Cli.bx` (verb registry, parsing, help, completions) and one class per verb with `run( options, config, verb )` returning `{ exitCode, message, data }`
- `src/main/bx/completions/bxPlaywright.bash`: generated from the verb registry, never edit by hand
- `src/main/java/ortus/boxlang/modules/playwright/`
  - `engine/`: `PlaywrightHome` (driver extraction, Node.js resolution, env), `NodeInstaller`, `Platform`, `OptionsMapper` (structs to Playwright options), `SmartSelector`, `Devices`, `PlaywrightErrors`
  - `components/PlaywrightRender.java`
- `src/test/java/`: JUnit tests. `e2e/` tests drive real browsers and only run with `PLAYWRIGHT_E2E=true`

## Commands

```bash
./gradlew downloadBoxLang                                  # once
./gradlew shadowJar test                                   # fast suite
PLAYWRIGHT_E2E=true ./gradlew shadowJar test               # with browsers (Node.js and Chromium land in build/playwright-home)
UPDATE_COMPLETIONS=true ./gradlew test --tests '*CliTest'  # regenerate completions after changing verbs
./gradlew shadowJar -Pflavor=full                          # full distribution
./gradlew spotlessApply                                    # format Java
```

## Conventions

- Ortus coding standards (see the `ortus-coding-standards` skill): tabs, spaces inside parentheses, aligned assignments, no semicolons in BoxLang.
- Every method in every class (BoxLang or Java, public or private, source, tests and fixtures) has a docblock: a description, every argument (`@name` in BoxLang, `@param` in Java) and `@return` when it returns something. `help()` builds its output from the BoxLang docblocks.
- Lambdas (`->`) only when the function uses nothing but its own arguments; closures (`=>`) otherwise.
- Every public DSL action returns the object for chaining. Every error is typed (`Playwright.*`) with a `detail` that explains the fix.
- Options are always a struct mapped by `OptionsMapper`: never hard-code empty Playwright options objects.
- Tests for every feature. Browser behavior goes in `e2e/` using the fake site in `src/test/resources/site` (served through request interception) or a local `HttpServer`.

## BoxLang Gotchas Found While Building This

- Built-in functions win over your own methods for unqualified calls: a method named `attempt` or `wrap` calls the BIF. Check names against `getFunctionList()`.
- Imports and variables are case insensitive: `import ...Key` clashes with `var key`, `import ...Devices` clashes with `var devices`.
- Scope names (`url`, `form`, `request`, `cookie`, `session`, `cgi`, ...) resolve to the scope on a web runtime, even as a `var`, a closure parameter, or an argument used without `arguments.`. Pick other names; `ScopeNamesTest` enforces it.
- `#` in strings starts interpolation: write `##id` for a literal `#id`.
- `property` declarations must come before any other statement in a class body.
- Relative `new models.X()` only resolves from `ModuleConfig.bx`; elsewhere use `new models.X@playwright()`.
- `duplicate()` deep copies and fails on Java objects: copy arrays of wrappers with `append( other, true )`.
- Java exceptions thrown by Playwright arrive wrapped; use `PlaywrightErrors.rootMessage()`/`rootType()`.
- Playwright evaluates regexes in the browser: no Java-only syntax such as `\Q...\E`.
- BoxLang closures are coerced to Java functional interfaces (Consumer, Runnable) when calling Playwright methods directly.
- A "Method not found" right after editing a class can be a stale compiled class. The Gradle `test` task now clears `~/.boxlang/classes` before running.
- `page.evaluate( "a = () => b" )` calls the resulting function: Playwright invokes any evaluated value that is a function. Wrap statements in `() => { ... }`.
- Only core BIFs are available to the module: `encodeForHTMLAttribute()`, `imageRead()` and friends come from optional modules (bx-esapi, bx-image) that users may not have. Local runs load every module in `~/.boxlang/modules`, so reproduce CI with a clean home: `GRADLE_USER_HOME=~/.gradle JAVA_TOOL_OPTIONS="-Duser.home=/tmp/cleanhome" ./gradlew test`.
- The tests fail if an installed copy of bx-playwright in `~/.boxlang/modules` shadows `build/module`: remove it before running them.
- Java `List` results (e.g. `consoleMessages()`) should be copied into a BoxLang array before using member functions such as `filter()`.

## Skills

Restore the pinned skills with `npx skills experimental_install` (see `skills-lock.json`). They install into `.claude/skills` (ignored by git).
