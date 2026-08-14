# HoloPanels

Interactive in-world panels. Text displays you can click, driven by config and extended by other plugins.

A panel is not a hologram with a command attached — it is a **view** made of panels, rendered per viewer, with its own conditions, pagination, selection state and click regions. Two players looking at the same board can see different things.

Runs on **Minecraft 1.19.4 – 26.x**, on Paper, Purpur, Pufferfish and Folia, from one jar. Java 17+. Requires [packetevents](https://github.com/retrooper/packetevents).

---

## Quick start

Drop the jar in `plugins/`, start the server, then:

```
/holopanels list                 boards and whether each one is placed
/holopanels here <board>         move a board to where you stand
/holopanels wall <board>         hang it on the block you are looking at
/holopanels reload               re-read boards and views
/holopanels validate             check the config without applying it
/holopanels debug                what is currently rendered to you
/holopanels refresh [board]      redraw
```

Boards live in `boards.yml`, views in `views/`. A board is a position in the world; a view is what it shows. Point several boards at one view and you have the same panel in several places.

`validate` is worth knowing about: it parses the whole configuration and reports problems **without** touching what is running, so you can check an edit before committing to it.

---

## Placing a board

`here` puts a board at your feet facing the way you look. `wall` is usually what you want instead: it raycasts up to 12 blocks, sits the board just off the face it hit and works out the yaw so the board faces out of the wall.

```
/holopanels wall <board> [gap]                     against the block you are looking at
/holopanels move <board> <x> <y> <z> [yaw]         exact coordinates
/holopanels nudge <board> <direction> [distance]   shift it along its own axes
/holopanels unplace <board>                        drop the anchor, keep the board
/holopanels remove <board> confirm                 delete it from boards.yml
```

In `move`, `~` keeps the board's current value for that coordinate — `move welcome ~ ~1 ~` raises it a block and `move welcome ~ ~ ~ 90` turns it where it stands. `nudge` takes `right`, `left`, `up`, `down`, `forward` or `back` in the board's own axes, the same ones panel offsets use, so a placement can be trimmed by tenths without touching a config file.

`unplace` and `remove` differ on purpose. `unplace` clears the anchor and leaves the board defined, ready to be placed again. `remove` deletes it from `boards.yml`, taking its view binding, distances and conditions with it — so it asks for `confirm` first.

Every one of these writes `boards.yml` and reloads, which means the usual reload safety applies: if the resulting file does not parse, the running boards keep going and you get a log message. Comments in `boards.yml` survive the rewrite.

---

## How it fits together

```
boards.yml     where a panel is, how far it is visible, who may see it
views/*.yml    what it shows: panels, lines, buttons, click actions
```

A **view** contains **panels** — `list`, `text` or `buttons` — each with its own offset, style and visibility condition. Lines are MiniMessage and support PlaceholderAPI.

Content can be static, or supplied by another plugin through a **provider**. That is what makes a leaderboard, a shop or a queue display possible without HoloPanels knowing what any of those are.

---

## Examples

[`examples/`](examples/) has six complete views that between them use every panel type, every built-in action and every condition kind — a warp directory with pagination and master–detail, a kit dispenser with click-to-confirm, a tabbed stats dashboard, a provider-fed leaderboard, a navigation hub and a dismissable notice board. Copy the ones you want into `plugins/HoloPanels/views/`.

---

## Reload safety

A reload builds the whole configuration first and only swaps it in if it parsed. A bad edit costs you a log message, not the plugin — the running boards keep working with the previous configuration until a valid one replaces it.

Configuration is then held as an immutable snapshot behind a single volatile reference, so a player's render never observes a half-applied reload.

---

## Compatibility

Purpur and Pufferfish are Paper forks and need nothing said about them — they are detected by probing for a class each one ships, never by reading a brand string, because forks routinely report themselves as their upstream.

The one thing that genuinely moves across the supported range is the Text Display metadata layout. 1.20.2 inserted a second interpolation duration into the Display entity, pushing every text field up by one index. These are wire indices, so nothing at compile time would have caught sending the wrong set — the panel would simply have come out blank.

| | Index layout |
|---|---|
| 1.19.4 – 1.20.1 | billboard 14, text 22 |
| 1.20.2 and later | billboard 15, text 23 |

Which one was picked is named in the startup block alongside the detected server and version, so a rendering bug report starts with the two facts it turns on.

**Folia is supported.** There is no `BukkitRunnable` or `Bukkit.getScheduler()` anywhere in the plugin; scheduling goes through Keystone's abstraction with a region-threaded backend. A refresh reads the player's position and the world around them, so it puts itself on the thread that owns that player rather than assuming one exists, and viewer sessions are concurrent because on Folia no single thread owns them. Teleports use the async form, and a console click action is dispatched on the global region rather than the clicking player's.

**Spigot is not supported**, and that is a consequence of the Adventure decision below rather than an oversight: `holopanels-api` publishes `Component` on its own surface, and bundling a relocated Adventure to reach Spigot would change that type out from under every provider written against it.

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

## Metrics

HoloPanels reports to [bStats](https://bstats.org/plugin/bukkit/HoloPanels/33368) and to a self-hosted endpoint at `plugins.metrics.bwmp.dev`, every 30 minutes: server software and Minecraft version, Java version, OS and core count, player count, and how much HoloPanels is doing — board, view and panel counts, open viewer sessions, registered extensions, which text display metadata layout the server needs, whether PlaceholderAPI is present, and which plugins depend on HoloPanels.

No addresses, no player names, no player UUIDs, no world data. The server is identified by a random UUID generated on first run and nothing else.

One switch turns off both, and covers every Keystone plugin on the server:

```yaml
# plugins/Keystone/telemetry.yml
enabled: false
```

Turning off bStats in `plugins/bStats/config.yml` also turns off the self-hosted half — one refusal covers both.

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

HoloPanels is the **Paper-family** Keystone build: it excludes Keystone's shaded Adventure and uses the server's own. That is not incidental. The renderer hands components straight to Paper's display APIs, and `holopanels-api` exposes `Component` on its own surface — relocating Adventure would move both into `dev.bwmp.holopanels.libs.kyori`, where neither lines up with the server, and every third-party provider would fail at runtime against a class name that looks correct.

It is also what draws the line at Spigot rather than at Purpur, Pufferfish or Folia, all of which ship Paper's Adventure and need nothing further.

CI asserts the jar contains no `kyori` classes for exactly this reason.

Dropping Adventure means `<relocations combine.self="override">`, which replaces the parent's list wholesale — so the `org.bstats.` entry has to be written back in by hand. Without it bStats finds itself unrelocated, throws from its own constructor, and the metrics sink logs a banner at every boot and sends nothing.

---

## Licence

[LGPL-3.0](LICENSE). Running it and writing providers against `holopanels-api` carries no obligations; modifying HoloPanels itself means publishing those modifications under the same licence.

`LICENSE` is the LGPL and `COPYING` is the GPL it builds on by reference — both are needed for the licence to be complete.
