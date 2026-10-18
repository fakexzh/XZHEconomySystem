# XZHEconomySystem 1.0.0 — integrated development branch

A Java plugin project targeting Purpur 26.3 (Java 25). This branch combines the economy, shop, loans, quests, death recovery, HUD, admin tools, and selected Essentials-style utilities. It is still under development and must be tested on a separate Purpur server before production use.

## Current features

- Persistent coin balances, `/balance`, `/pay`, and admin `/eco`.
- Shop GUI with confirmation dialogs, quantities, curated products, global stock, daily restock, and cheap sell-hand support for unfeatured items.
- Enchantment-book GUI with confirmation, 24-hour per-player cooldown per enchantment, and daily global limits for selected useful/rare books.
- Elytra purchase lock until the player has visited the End.
- Loan GUI, configured terms/rates, 12-hour grace period, automatic repayment when funds are sufficient, and shop purchase restriction once overdue.
- Quest packs with shared daily stock, active quest boss bar/progress, 30-minute actionbar reminders, and a daily global community ore event.
- Paid death inventory recovery: 30-minute window, two recoveries per day, configured recovery fee, and death money penalty.
- HUD sidebar for balance, amount owed, and due/overdue time.
- Admin GUI `/xzhadmin` for balance updates, shop stock management, transaction history, and diagnostics.
- Essentials-style commands: spawn, homes, warps, TPA, back, private messages, ignore, AFK, near, list, top, and RTP.
- Librarian enchanted-book trade blocking.
- YAML persistence for balances, loans, quests, shop stock, book cooldowns/stock, death recovery, and locations; balance changes are appended to a transaction log.

## Commands

Player:
- `/xzh`
- `/balance`, `/bal`, `/money`
- `/pay <player> <amount>`
- `/shop`, `/books`
- `/loan`, `/loan repay`
- `/quests`
- `/deathinventory`
- `/spawn`, `/sethome [name]`, `/home [name]`, `/delhome [name]`
- `/warp <name>`, `/tpa <player>`, `/tpaccept`, `/tpdeny`, `/back`
- `/msg <player> <message>`, `/reply <message>`, `/ignore <player>`
- `/afk`, `/near`, `/list`, `/top`, `/rtp`

Admin (permission `xzh.admin`, default OP):
- `/eco <set|add> <online-player> <amount>`
- `/xzhadmin`
- `/shopadmin restock`
- `/shopadmin stock <MATERIAL> <amount>`
- `/setspawn`, `/setwarp <name>`, `/delwarp <name>`

## Build and install

Requires JDK 25. Run `gradle clean build` or use the GitHub Actions workflow. Successful workflow runs publish a JAR artifact and a source ZIP.

See [configuration documentation](docs/CONFIGURATION.md) and the [Falix install guide](docs/FALIX_INSTALL.md).

## Known remaining work before a complete release

The current branch still needs a full feature audit and live-server testing. In particular, verify every GUI/action against the final specification, review all loan overdue/grace behavior, expand the admin GUI to manage every subsystem, validate item pricing/restrictions and shop stock persistence, and confirm death recovery/XP behavior through real deaths and restarts. A successful compile is not a substitute for a runtime test on Purpur 26.3.
