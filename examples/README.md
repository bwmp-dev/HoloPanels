# Example panels

Six views that between them use every panel type, every built-in action and
every condition kind. They are not shipped inside the jar — copy what you want.

```
cp examples/views/*.yml   plugins/HoloPanels/views/
cp examples/boards.yml    plugins/HoloPanels/boards.yml   # or merge by hand
```

Then, in game:

```
/holopanels validate            check the files without applying them
/holopanels reload
/holopanels here welcome        stand where you want it, facing away from it
/holopanels wall hub_board      or look at a wall and let it work out the facing
```

A board faces the direction you were looking when you placed it, and panel
offsets are relative to that facing — `right` and `forward` follow the board,
not the compass. `/holopanels nudge <board> right 0.2` moves it along those same
axes, which is the quickest way to line one up after placing it.

## Stacking panels vertically

A panel's `up` offset is its **bottom edge**, and it grows upward from there.
A panel is `lines x line-height` tall, so with the default `line-height: 0.25`
a five-row panel at `up: 0.0` occupies `0.0` to `1.25`, and anything sitting
above it needs `up: 1.25` or more.

The trap is that a panel's height is not fixed. Buttons with `visible-if`
appear and disappear, lists grow to `page-size`, and provider-fed content is
whatever the provider returns — so budget for the **tallest** state a panel can
reach, not the one in front of you. A layout tuned against four visible buttons
will have the fifth slide under the panel above it.

---

## What each one demonstrates

| File | Panels | Worth stealing |
|---|---|---|
| [`welcome.yml`](views/welcome.yml) | text, buttons | Placeholders, a chat link, and dismissing a board |
| [`hub.yml`](views/hub.yml) | text, buttons | One board hosting several views via `open-view` |
| [`warps.yml`](views/warps.yml) | list, text, buttons | Pagination, master–detail, entry attributes, four click types |
| [`kits.yml`](views/kits.yml) | list, text, buttons | Click-to-confirm, per-action permissions, console commands |
| [`stats.yml`](views/stats.yml) | text, buttons | A tab strip built out of session state |
| [`leaderboard.yml`](views/leaderboard.yml) | list, text, buttons | Providers, custom actions and custom conditions |

`leaderboard.yml` is the only one that needs a companion plugin. The rest work
with nothing but HoloPanels, and `welcome.yml` / `stats.yml` want
PlaceholderAPI for their numbers — without it the `%…%` tokens render as
literal text, which is a fast way to check whether it loaded.

---

## The ideas these lean on

**Everything is per viewer.** Selection, page, session state and which view a
board is showing all live on a `(player, board)` session. Two players at one
board can be on different pages of different views. Nothing is global, so
nothing has to be locked.

**Conditions gate rendering, not just appearance.** A panel or button that
fails `visible-if` is not drawn *and* has no click region. Hiding a staff
button is a real restriction, not a cosmetic one. Per-action `permission:` is
the complement, for when the control should be visible but refuse.

**Selection is a link between panels.** A `list` panel owns the selection;
another panel points at it with `selection: <panel-id>`, which is what makes
`<entry:field>` resolve there. `visible-if: { selected: <panel-id> }` is the
usual companion so the detail panel disappears when nothing is picked.

**Actions run as a chain and stop at the first failure.** In `kits.yml` the
`title` never fires on the click that only arms the confirmation, because the
confirmation returns a denial. Add `continue-on-failure: true` to an action to
carry on regardless.

**Only some strings are templated.** Anything shown to the player —
`lines`, `row`, button `text`, and an action's `message`, `title`,
`denied-message`, `confirm-message` — is MiniMessage with `<entry:…>`,
`<state:…>`, `<player:…>` tokens and PlaceholderAPI. Action *arguments* are
not. A `command` action gets exactly three substitutions, `<player>`,
`<uuid>` and `<entry_id>`, which is why per-entry travel in `warps.yml` runs
`warp <entry_id>` rather than templating a `teleport` action's coordinates.

---

## Making the leaderboard work

`leaderboard.yml` names four things that a companion plugin registers. The
whole surface is four methods:

```java
HoloPanels api = Bukkit.getServicesManager().load(HoloPanels.class);
NamespacedKey topBalances = new NamespacedKey(this, "top_balances");

api.registerEntryProvider(this, topBalances, request ->
    CompletableFuture.supplyAsync(() -> economy.topBalances(100).stream()
        .map(row -> PanelEntry.builder(row.id(), Component.text(row.name()))
            .field("rank", Component.text("#" + row.rank()))
            .field("balance", Component.text(row.formatted()))
            .field("last_seen", Component.text(row.lastSeen()))
            .attribute("staff", String.valueOf(row.isStaff()))
            .build())
        .toList()));
```

Field names are yours; `<entry:balance>` in the view resolves whatever you
attached under `balance`. `label` is always the entry's label, and attributes
are the plain strings that `entry-attribute` conditions compare.

The provider is called off the main thread and its answer is cached on the
viewer's session, so a database query does not stall the tick — the first
render shows the panel's `empty` text and redraws when the result lands. Call
`api.refresh(player, boardId)` to drop that cache and ask again.

Ids must be in your own plugin's namespace, and everything you register is
dropped when your plugin disables, so a reload cannot leave a board pointing
at a dead classloader.

---

## Layout notes

A panel is centred on its offset, and its height grows downward *and* upward
as lines are added. Two panels of different heights sharing an `up` value will
not have their first lines aligned — nudge `up` per panel until they look
right, or give the shorter one a blank leading line.

`interaction-width` (default `4.0`) is how wide the clickable region is, not
how wide the text is; `line-width` is the text wrap point in pixels. Side by
side panels want their `right` offsets at least `interaction-width` apart or
their click regions overlap and the nearer one wins.

Panels with `background-opacity: 0.0` still occupy space and still take
clicks. That is what the button strips here use so they read as floating text
under a solid panel.
