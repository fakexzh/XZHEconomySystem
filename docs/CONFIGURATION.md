# XZHEconomySystem configuration

This document describes the settings currently implemented by the development branch for Purpur 26.3.

## Economy

```yaml
currency-name: coins
starting-balance: 100.0
minimum-pay: 1.0
maximum-pay: 1000000.0
```

- `currency-name`: label used in balance messages.
- `starting-balance`: initial balance for a UUID that has never had a saved balance.
- `minimum-pay` / `maximum-pay`: accepted range for `/pay`.

## HUD

```yaml
hud:
  scoreboard:
    enabled: true
    position: sidebar
  quest-bossbar:
    enabled: true
```

The sidebar shows balance, active loan amount owed, and time remaining/overdue state. The active quest boss bar is driven by quest progress.

## Shop global stock

```yaml
shop:
  stock-limits:
    coal: 2000
    iron_ingot: 1000
    gold_ingot: 500
    diamond: 250
    obsidian: 250
    bread: 1000
    cooked_beef: 1000
    iron_pickaxe: 100
    diamond_pickaxe: 30
    elytra: 3
```

Stock is shared across all players and saved in `shop-stock.yml`. Each configured value is the daily restock cap. The administrator command `/shopadmin stock <MATERIAL> <amount>` changes current stock; `/shopadmin restock` refills configured shop products immediately.

## Death recovery

```yaml
death-recovery:
  fee-multiplier: 1.5
  xp-value-per-point: 0.25
  material-values:
    elytra: 2500
    netherite_ingot: 300
    diamond: 80
    diamond_block: 720
    ancient_debris: 120
```

- Recovery window: 30 minutes after death.
- Daily limit: 2 successful recoveries per player.
- Fee: estimated lost value multiplied by `fee-multiplier`.
- On death, the money penalty is 10% of estimated lost value, bounded to 10–1000 coins and capped by the player's available balance.
- Per-material entries override the built-in estimate. Use lowercase material names.

## Data files

The plugin maintains separate YAML files for balances, loans, quests, shop stock, and death recovery. Back up the server and these files before testing a development build.

## Important scope note

These docs describe settings that exist in the current branch. They do not mean every item from the full release specification is complete. The branch still requires additional feature work and in-server testing before it should be treated as a finished production release.
