# CritBot (Fabric 1.21.11, client-side)

Hotkey toggle (default R, rebind in Options -> Controls -> Miscellaneous -> "Toggle CritBot"):
- targets the nearest hostile mob or player that wears/holds any netherite item (armor, sword, axe...)
- ignores invisible entities
- crits only: jumps and hits on the way down with a fully charged weapon; never makes a non-crit hit
- auto-dodge: only vs players (entities that can miss); sidesteps when a player aimed at you is within reach

Build: Java 21, `gradle build` -> `build/libs/critbot-1.0.0.jar` (needs Fabric API).
Note: on multiplayer servers this counts as a cheat; use where allowed.
