- Saved sessions, artifacts, snapshots and the axe script are read and written on their real paths, never through BoxLang mappings. With `HOME=/root` and an application `/root` mapping (common in ColdBox apps tested in Docker as root), `session()` wrote the session inside the application while `browse( { session } )` read the real home, so the session never existed.
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

- First release of the bx-playwright module.
