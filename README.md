# EternalCombat-OG

A [TrueOG Network](https://true-og.net) fork of [EternalCombat](https://github.com/EternalCodeTeam/EternalCombat)
by the EternalCode Team — a combat-tag / combat-logging plugin for Paper.

## License & attribution

EternalCombat-OG is a derivative work of **EternalCombat** by the **EternalCode Team**, distributed
under the **Apache License 2.0**. The original copyright and license are retained; see the
[`LICENSE`](LICENSE) file. Upstream project: https://github.com/EternalCodeTeam/EternalCombat

## Changes from upstream

This fork diverges from upstream EternalCombat in the following ways:

- Renamed the project and plugin to `EternalCombat-OG`.
- Fixed combat knockback flinging players across the map or into the sky near safe zones.
- Players in combat can move between the warzone and wilderness, but not into safe areas.
- Render the glass combat border on the true safe/unsafe boundary (e.g. between the warzone and spawn or the market) instead of a region's bounding box, so no wall shows between the warzone and wilderness or on the outer face of a large enclosing region.
- Mounted players are pushed along with their mount instead of being thrown off.
- Closed gaps that let combat-tagged players slip into shops and the market.
- Removed chat gradients.
- Added a command whitelist for "The Herobrine" minigames.
- Dropped Lands support.
- Removed the upstream GitHub Actions, plus build and dependency cleanup.

## Requirements

- Paper (Java 17+ toolchain)
- [PacketEvents](https://modrinth.com/plugin/packetevents) (required)
- WorldGuard (optional, for region-based combat restrictions)
