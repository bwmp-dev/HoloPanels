# Changelog

## 1.0.0 (2026-08-10)


### Features

* initial release of HoloPanels ([8fddd82](https://github.com/bwmp-dev/HoloPanels/commit/8fddd8237f09b4ca2079c2aa9f0edc8964e4b149))

## Changelog

Notable changes to HoloPanels. Versions follow [semantic versioning](https://semver.org);
`holopanels-api` is the surface those promises apply to.

## Unreleased

First release.

### Panels

- Views made of `list`, `text` and `buttons` panels, rendered per viewer with
  their own offsets, styles, visibility conditions, pagination and selection.
- Boards place a view in the world. Several boards may share one view.
- Click regions map to actions, including actions supplied by other plugins.
- MiniMessage throughout, with PlaceholderAPI where it is installed.

### Configuration

- `/holopanels validate` parses the whole configuration and reports problems
  **without** applying it, so an edit can be checked before it is committed to.
- A reload builds the replacement first and only swaps it in if it parsed. A bad
  edit costs a log message rather than the plugin.
- The active configuration is an immutable snapshot behind a volatile reference,
  so a render never observes a half-applied reload.

### API

- `EntryProvider`, `ContentProvider`, `ConditionEvaluator` and `ActionHandler`
  extension points.
- Registrations are namespace-checked against the owning plugin and dropped
  automatically when it disables, so a reload cannot leave a stale handler
  pointing at a dead classloader.

### Built on Keystone

- Messages, config handling, the command tree, registries and scheduling now
  come from [Keystone](../Keystone), replacing hand-rolled equivalents.
- Scheduling goes through Keystone's abstraction, which makes HoloPanels
  Folia-capable.
- **This is the Paper-only Keystone build.** Keystone's shaded Adventure is
  excluded and the server's own is used, because the renderer hands components
  to Paper's display APIs and `holopanels-api` publishes `Component` on its own
  surface. Relocating it would break both. CI asserts no `kyori` classes end up
  in the jar.
