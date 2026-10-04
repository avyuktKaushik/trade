# OpTrade

A Paper 1.21.x plugin where an op starts a trade with another player in a shared GUI.

## How it works
- `/trade <player>` — ops only (`optrade.use`, default: op). Opens the trade GUI for you **and** the other player.
- Left 4 columns = the op's offer, right 4 columns = the other player's offer. You can only put items on / take items from your own side.
- Each player clicks their own accept button (red → green). When both have accepted, a **5-second countdown** starts.
- If **any** item on either side is added, removed or changed, both accepts reset and the countdown is cancelled.
- Closing the GUI, logging out, or the server stopping cancels the trade and everyone gets their own items back.
- When the countdown finishes, items are swapped. If an inventory is full, the extra items drop at that player's feet.

## Build
Requires Java 21.

```
./gradlew build
```

The jar is in `build/libs/OpTrade-1.0.0.jar` — drop it into your server's `plugins/` folder.

## Config
`plugins/OpTrade/config.yml` — countdown length, GUI title and every message (MiniMessage format).
