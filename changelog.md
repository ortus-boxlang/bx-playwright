# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

- First release of the bx-playwright module.
- `playwrightEnsureBrowser( browser, channel )`: with a channel such as `chrome` or `msedge`, it uses the browser installed on the machine and downloads nothing.
- A failed browser install reports the last lines of the Playwright CLI output in the `Playwright.NotInstalled` error detail.
- Browser downloads keep Playwright's 30 second connection timeout (was 2 minutes), so a blocked network fails in about 2.5 minutes over Playwright's 5 attempts instead of 10.