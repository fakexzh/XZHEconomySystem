# XZHEconomySystem 1.0.0

A first deployable baseline for Paper/Purpur 26.3. This is **not yet the complete planned EssentialsX/economy/shop/quest replacement**; it is a small real-API build intended to verify loading and basic economy behavior first.

## Commands
- `/xzh` — help
- `/balance` or `/bal` — own balance
- `/balance <online-player>` — another online player's balance
- `/pay <online-player> <amount>` — transfer coins
- `/eco set <online-player> <amount>` and `/eco add <online-player> <amount>` — admin economy commands (`xzh.admin`, default OP)

Balances persist in `plugins/XZHEconomySystem/balances.yml`. The default starting balance is 100 coins and can be changed in `config.yml`.

## Build
Requires JDK 25 and an internet connection. Run `gradle build` or use the included GitHub Actions workflow. The Paper API is a compile-only dependency; it is supplied by the server at runtime.

## Falix install
1. Make sure the server software is Paper or Purpur 26.3 and its Java runtime is Java 25.
2. Upload `XZHEconomySystem-1.0.0.jar` to `plugins/`.
3. Fully stop and start the server.
4. Check `/plugins`, then run `/xzh` and `/balance`.
5. If it fails, send the startup error from the console.
