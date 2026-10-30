# FoDrops

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fodrops) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fodrops/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

An in-game custom drop editor with configurable reward delivery.

*Developed by Carrotio.*

## Features

- Creates configurable custom drop profiles through an in-game editor.
- Supports targeted reward delivery for configured game events.
- Allows item and command-driven reward behavior with safety limits.
- Keeps drop definitions, feedback, and sounds configurable.
- Fires a cancellable reward event that integrations can modify or handle.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fodrops). Check the [versions page](https://modrinth.com/plugin/fodrops/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** FoReforge. Its bundled drop profiles appear only while FoReforge is installed; untouched defaults are removed when it is absent. Edited profiles are preserved.

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fodrops <version|editor|reload>` — Main command for FoDrops. Aliases: `/drops`.

### Permissions

- `fodrops.admin` — Allows full access to FoDrops commands and editor. Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `drops.yml` — Custom drop definitions.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `limits`, `safety`, `native-dialogs`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

Build against the matching FoDrops jar as a provided dependency; do not shade it into a consumer.
For example, place the built `FoDrops-2.6.jar` in `libs/` and use
`compileOnly(files("libs/FoDrops-2.6.jar"))` in Gradle. Add `softdepend: [FoDrops]`
to a Bukkit `plugin.yml`, or `depend: [FoDrops]` when FoDrops is required. Keep
direct references in a separate integration class loaded only when FoDrops is
enabled. The service is registered during enable and removed during disable;
look it up again after a provider restart.

`FoDropsApi` is available through Bukkit's services manager. Its methods require
the primary server thread; never call them from an async worker. The block-tag
query checks active profile conditions without observing or starting cooldowns.
It does not predict whether `stop-processing` or a selection veto will prevent
the profile from being selected. Snapshots copy active in-memory profile and
entry metadata, omitting conditions and commands. They do not read `drops.yml`
directly and do not change after they are returned. A disabled provider returns
`false`/empty snapshots or `UNAVAILABLE` from `trigger`.

~~~java
FoDropsApi api = Bukkit.getServicesManager().load(FoDropsApi.class);
if (api != null) {
    boolean tagged = api.matchesBlockProfileTag(player, block, tool, "my-tag");
    List<FoDropProfileSnapshot> profiles = api.getProfiles();
    FoDropTriggerResult result = api.trigger(new FoDropTriggerRequest(
        "BLOCK_BREAK", player, block.getLocation(), tool, block.getType().name(), Map.of()));
}
~~~

`trigger` evaluates configured profiles using the supplied context and runs
FoDrops' normal reward path. Call it only from the primary server thread for a
separate activity that FoDrops does not already process; otherwise rewards can
be duplicated. It rejects unknown event types and locations without worlds.
`PROCESSED` means at least one profile was selected, not that a random item was
won. `NO_MATCH` also covers profiles vetoed by listeners; `REENTRANT` rejects
a nested trigger call made during another trigger's events. The result's
`wouldCancelVanillaDrops` is information for the caller; the API never modifies
the caller's Bukkit event. The request and result copy their mutable values.

Events fire in the caller's event context, synchronously and on the same thread.
Consumers must follow Bukkit's world/player thread rules. Timing:

- `FoDropProfileSelectEvent`: per eligible profile, after condition matching but
  before selection, cooldown marking, `stop-processing`, or vanilla-drop changes.
  Cancellation skips only that profile.
- `FoDropEvaluationRollEvent`: after selection, before entry rolls; permits up to
  eight additional reruns of the selected profiles. The list can be empty on
  the explosion-death path.
- `FoDropEntryChanceEvent`: before each entry chance roll, including bonus rolls;
  listeners may adjust chance from 0 to 100.
- `FoDropRewardEvent`: before each item reward chunk is delivered. Listeners may
  replace the item, cancel it, or cancel and set `handled` after taking delivery
  themselves. `setHandled(true)` alone does not stop FoDrops delivery. Its new
  `getEntryId()` identifies the configured entry; the old constructor remains.
- `FoDropItemRewardProcessedEvent`: after FoDrops delivers a valid chunk or a
  listener cancels and marks it handled. Its outcome distinguishes FoDrops
  delivery from external handoff. The event is informational and cannot cancel
  delivery. Ground rewards added to another Bukkit event's drop list may still
  be changed later by that event's other listeners.

`FoDropRewardEvent` and the processed event cover item rewards only. Command-only
rewards do not fire them. Event locations and items are defensive copies; profile
and entry IDs are stable identifiers within the current `drops.yml` configuration.

</details>
