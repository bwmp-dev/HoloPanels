# HoloPanels

Interactive in-world panels for Paper servers. Text displays you can click, driven by config and extended by other plugins.

A panel is not a hologram with a command attached — it is a **view** made of panels, rendered per viewer, with its own conditions, pagination, selection state and click regions. Two players looking at the same board can see different things.

Requires **Paper 1.19.4+** and [packetevents](https://github.com/retrooper/packetevents). Java 17+.

---

## Quick start

Drop the jar in `plugins/`, start the server, then:

```
/holopanels list                 boards and whether each one is placed
/holopanels here <board>         move a board to where you stand
/holopanels reload               re-read boards and views
/holopanels validate             check the config without applying it
/holopanels debug                what is currently rendered to you
/holopanels refresh [board]      redraw
```

Boards live in `boards.yml`, views in `views/`. A board is a position in the world; a view is what it shows. Point several boards at one view and you have the same panel in several places.

`validate` is worth knowing about: it parses the whole configuration and reports problems **without** touching what is running, so you can check an edit before committing to it.

---

## How it fits together

```
boards.yml     where a panel is, how far it is visible, who may see it
views/*.yml    what it shows: panels, lines, buttons, click actions
```

A **view** contains **panels** — `list`, `text` or `buttons` — each with its own offset, style and visibility condition. Lines are MiniMessage and support PlaceholderAPI.

Content can be static, or supplied by another plugin through a **provider**. That is what makes a leaderboard, a shop or a queue display possible without HoloPanels knowing what any of those are.

---

## Reload safety

A reload builds the whole configuration first and only swaps it in if it parsed. A bad edit costs you a log message, not the plugin — the running boards keep working with the previous configuration until a valid one replaces it.

Configuration is then held as an immutable snapshot behind a single volatile reference, so a player's render never observes a half-applied reload.

---

## For developers

HoloPanels publishes `holopanels-api` with four extension points:

| Interface | Supplies |
|---|---|
| `EntryProvider` | rows for a `list` panel |
| `ContentProvider` | lines for a `text` panel |
| `ConditionEvaluator` | a custom `visible-if` condition |
| `ActionHandler` | a custom click action |

```java
HoloPanels api = Bukkit.getServicesManager().load(HoloPanels.class);
api.registerEntryProvider(this, new NamespacedKey(this, "top_balances"), request -> ...);
```

Ids must be in your own plugin's namespace, and everything you register is dropped automatically when your plugin disables — so a reload cannot leave a stale handler pointing at a dead classloader.

---

## Building

HoloPanels shades [Keystone](https://github.com/bwmp-dev/Keystone). Keystone is published, so `mvn install` is all you need:

```
mvn install
```

`keystone-parent` is this module's Maven parent, and Maven resolves a parent *before* it reads the project's own `<repositories>` — so the Nexus repository has to come from your `~/.m2/settings.xml`:

```xml
<profiles>
  <profile>
    <id>bwmp-nexus</id>
    <repositories>
      <repository>
        <id>bwmp-nexus</id>
        <url>https://nexus.bwmp.dev/repository/maven-public/</url>
      </repository>
    </repositories>
  </profile>
</profiles>
<activeProfiles><activeProfile>bwmp-nexus</activeProfile></activeProfiles>
```

Building Keystone from source and `mvn install`-ing it locally also works, and is the way to test an unreleased change:

```
git clone https://github.com/bwmp-dev/Keystone && cd Keystone && mvn install
```

### A note on Adventure

HoloPanels is the **Paper-only** Keystone build: it excludes Keystone's shaded Adventure and uses the server's own. That is not incidental. The renderer hands components straight to Paper's display APIs, and `holopanels-api` exposes `Component` on its own surface — relocating Adventure would move both into `dev.aether.holopanels.libs.kyori`, where neither lines up with the server, and every third-party provider would fail at runtime against a class name that looks correct.

CI asserts the jar contains no `kyori` classes for exactly this reason.

---

## Licence

[LGPL-3.0](LICENSE). Running it and writing providers against `holopanels-api` carries no obligations; modifying HoloPanels itself means publishing those modifications under the same licence.

`LICENSE` is the LGPL and `COPYING` is the GPL it builds on by reference — both are needed for the licence to be complete.
