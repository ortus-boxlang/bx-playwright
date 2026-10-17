# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

- First release of the bx-playwright module.
- With the bx-ai module loaded, the browser tools register themselves in `aiToolRegistry()` as `browser_*@bxplaywright`, so an agent picks them by name or all at once with `getByModule( "bxplaywright" )`. Nothing starts until a tool is used. Turn it off with `registerAiTools : false`. `aiTools()` no longer leaks the tools into the registry under bare names.
