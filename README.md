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

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoDrops` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoDrops</artifactId>
  <version>2.6</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foDrops.api.FoDropsApi;
import me.foesio.foDrops.api.FoDropEntryChanceEvent;

FoDropsApi api = Bukkit.getServicesManager().load(FoDropsApi.class);
if (api != null) api.getProfiles();

@EventHandler
public void onFoDropEntryChanceEvent(FoDropEntryChanceEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `FoDropsApi`: `matchesBlockProfileTag`, `getProfiles`, `getProfile`, `trigger`.

**Other API types:** `FoDropEntrySnapshot`, `FoDropProfileSnapshot`, `FoDropTriggerRequest`, `FoDropTriggerResult`.

**Events:** `FoDropEntryChanceEvent`, `FoDropEvaluationRollEvent`, `FoDropItemRewardProcessedEvent`, `FoDropProfileSelectEvent`, `FoDropRewardEvent`.

[Browse API source](src/main/java/me/foesio/foDrops/api) for signatures and event details.

</details>
