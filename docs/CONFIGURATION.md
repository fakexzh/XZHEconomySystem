# XZHEconomySystem configuration

This document describes the settings currently implemented by the development branch for Purpur 26.3.

## Economy

The plugin registers as a Vault economy provider when Vault is installed. If another economy plugin is installed too, make sure XZHEconomySystem is intended to be the primary provider.

```yaml
currency-name: coins
currency-name-singular: coin
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

Stock is shared across all players and saved in `shop-stock.yml`. Each configured value is the daily restock cap. Buy/sell prices can be overridden under `shop.prices.<material>.buy` and `shop.prices.<material>.sell`; unfeatured held items use `shop.default-sell-price`. The administrator command `/shopadmin stock <MATERIAL> <amount>` changes current stock; `/shopadmin restock` refills configured shop products immediately.

## Death recovery

```yaml
death-recovery:
  overworld-only: true
  fee-multiplier: 1.5
  xp-value-per-point: 0.25
  material-values:
    elytra: 2500
    netherite_ingot: 600
    netherite_scrap: 150
    diamond: 125
    diamond_block: 720
    ancient_debris: 120
```

- Recovery window: 30 minutes after death.
- Daily limit: 2 successful recoveries per player.
- Fee: estimated lost value multiplied by `fee-multiplier`.
- On death, the money penalty is 10% of estimated lost value, bounded to 10–1000 coins and capped by the player's available balance.
- Per-material entries override the built-in estimate. Use lowercase material names.

## Quests and global event

```yaml
quests:
  pack-stock:
    beginner: 50
    miner: 30
    hunter: 25
    explorer: 15
    nether: 10
    end: 5
  global-event:
    goal: 500
    reward: 100
    claims-per-day: 25
```

Quest pack stock resets to these configured values each server-local day. The global event uses a shared ore goal, pays the configured reward, and allows the configured number of successful claims per day. `/xzhadmin` can also change the current pack stock until the next daily reset.

## Gameplay restrictions

```yaml
restrictions:
  disable-librarian-enchanted-book-trades: true
  elytra-requires-end-visit: true
  buy-only-spawn-eggs: true
```

- `disable-librarian-enchanted-book-trades`: when enabled, librarian enchanted-book offers are blocked and existing offers are removed when interacted with. Turning it off allows future offers, but recipes removed while the restriction was enabled may not be restored automatically.
- `elytra-requires-end-visit`: when enabled, players must unlock the vanilla End-visit advancement before buying an Elytra.
- `buy-only-spawn-eggs`: when enabled, spawn eggs cannot be sold back to the shop. When disabled, eggs default to a 30% sellback unless a material-specific sell price overrides it.

## Enchantment books

The book shop is available through `/books` and the main `/shop` menu. Each player has a configurable cooldown per enchantment, while selected useful/rare books have a shared global daily stock cap.

```yaml
book-shop:
  cooldown-hours: 24
  rare-daily-stock: 3
  normal-daily-stock: 100
  prices:
    mending: 1200
    wind_burst: 1500
    swift_sneak: 700
    soul_speed: 700
    frost_walker: 700
```

Price entries are optional overrides; omitted entries use built-in defaults. Selected rare books (including Mending) reset to the rare daily stock limit each day. Administrators can clear an individual player's book cooldowns from `/xzhadmin`.

## Player utilities

```yaml
utilities:
  max-homes: 3
  rtp-radius: 3000
  rtp-cooldown-seconds: 600
```

The random teleport search is centered on the configured spawn when available and uses a safe-location check. The cooldown is currently in-memory and resets on a server restart.

## Starter kit

```yaml
kits:
  starter:
    cooldown-hours: 24
    items:
      - STONE_PICKAXE:1
      - BREAD:8
      - TORCH:16
```

Item entries use `MATERIAL:AMOUNT`. The kit cooldown is persisted per player in `locations.yml`. The `xzh.kit.starter` permission defaults to true.

## Data files

The plugin maintains separate YAML files for balances, loans, quests, shop stock, and death recovery. Back up the server and these files before testing a development build.

## Important scope note

These docs describe settings that exist in the current branch. They do not mean every item from the full release specification is complete. The branch still requires additional feature work and in-server testing before it should be treated as a finished production release.
