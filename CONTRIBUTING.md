# Contributing

## Commit messages decide the version

Releases are automated with [release-please](https://github.com/googleapis/release-please). It reads [Conventional Commits](https://www.conventionalcommits.org) from the default branch and keeps a release PR open with the next version and a generated changelog. **Merging that PR cuts the release.**

| Prefix | Effect | Use for |
|---|---|---|
| `fix:` | patch | bug fixes |
| `feat:` | minor | new behaviour |
| `feat!:` or a `BREAKING CHANGE:` footer | major, once past 1.0 | anything that breaks `holopanels-api` or a config format |
| `docs:` `chore:` `refactor:` `test:` `ci:` | no release | everything else |

Force a version with a `Release-As: 1.0.0` footer on any commit.

## What counts as breaking

`holopanels-api` is what third-party providers compile against — `EntryProvider`, `ContentProvider`, `ConditionEvaluator`, `ActionHandler`. Changing any of them breaks other people's plugins.

Config formats count too. Renaming a key in `boards.yml` or a view file breaks every server that already wrote one, so it needs a migration or a deprecation period rather than a rename.

## Adventure must not be bundled

HoloPanels is the **Paper-only** Keystone build. It excludes Keystone's shaded Adventure and uses the server's own, because the renderer hands components straight to Paper's display APIs and `holopanels-api` publishes `Component` on its own surface. Bundling Adventure would relocate both into `dev.aether.holopanels.libs.kyori`, where neither lines up with the server — and every provider plugin would fail at runtime against a class name that looks entirely correct.

CI fails the build if any `kyori` class ends up in the jar. If that check trips, the exclusion in `holopanels-plugin/pom.xml` has regressed; do not delete the check.

## Tests

`PanelGeometryTest` and `ViewerSessionTest` cover the parts with real logic in them — layout maths and per-viewer session state. They run on every build and are the reason a refactor of the render path can be trusted. Keep them passing rather than adjusting them to match new behaviour, unless the behaviour change is the point of the commit.

## Versions in poms are generated

```xml
<version>0.1.0</version> <!-- x-release-please-version -->
```

Do not edit annotated versions by hand, and do not annotate the `keystone-parent` version — that belongs to another repository, and bumping it would point HoloPanels at a Keystone release that does not exist.

## Before opening a PR

```
mvn install    # in Keystone first, then here
```
