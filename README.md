# XZHEconomySystem 1.0.0 — integrated development branch

A Java plugin project targeting Purpur 26.3 (Java 25). This branch combines the economy, shop, loans, quests, death recovery, HUD, admin tools, and selected Essentials-style utilities. It is still under development and must be tested on a separate Purpur server before production use.

## Current features

- Physical, server-tagged XZH pocket coins in 1/10/100/1000 denominations that naturally drop on death, separate persistent bank balances, `/balance`, `/pay`, admin `/eco`, and an optional Vault bridge.
- Shop GUI with confirmation dialogs, quantities, curated products, global stock, daily restock, and cheap sell-hand support for unfeatured items.
- Enchantment-book GUI with confirmation, 24-hour per-player cooldown per enchantment, and daily global limits for selected useful/rare books.
- Elytra purchase lock until the player has visited the End.
- `/bank` opens the separate `XZH BANK` GUI with persistent bank balances, 09:00–20:00 Minecraft-time deposit/withdrawal hours, and the Loans menu. Shop purchases and loan repayments spend pocket funds first, then bank funds; bank-funded purchases and repayments remain available after closing time.
- Loan GUI inside the bank, configured terms/rates, 12-hour grace period, automatic repayment from combined pocket/bank funds when sufficient, and shop purchase restriction once overdue.
- Quest packs with shared daily stock, active quest boss bar/progress, 30-minute actionbar reminders, and a daily global community ore event.
- Paid death inventory recovery: 30-minute window, two recoveries per day, configured recovery fee, and death money penalty.
- HUD sidebar for balance, amount owed, and due/overdue time.
- Admin GUI `/xzhadmin` for balance updates, shop stock management, transaction history, and diagnostics.
- Essentials-style commands: spawn, homes, warps, TPA, back, private messages, ignore, AFK, near, list, top, RTP, configurable starter kit, and help.
- Librarian enchanted-book trade blocking.
- YAML persistence for balances, loans, quests, shop stock, book cooldowns/stock, death recovery, and locations; balance changes are appended to a transaction log.

## Commands

Player:
- `/xzh`
- `/balance`, `/bal`, `/money`
- `/pay <player> <amount>`
- `/shop`, `/bank`, `/books`
- `/loan` (opens the bank), `/loan repay`
- `/quests`
- `/deathinventory`
- `/spawn`, `/sethome [name]`, `/home [name]`, `/delhome [name]`
- `/warp <name>`, `/tpa <player>`, `/tpaccept`, `/tpdeny`, `/back`
- `/msg <player> <message>`, `/reply <message>`, `/ignore <player>`
- `/afk`, `/near`, `/list`, `/top`, `/rtp`, `/kit starter`, `/help

Admin (permission `xzh.admin`, default OP):
- `/eco <set|add> <online-player> <amount>`
- `/xzhadmin`
- `/shopadmin restock`
- `/shopadmin stock <MATERIAL> <amount>`
- `/setspawn`, `/setwarp <name>`, `/delwarp <name>`

## Build and install

Requires JDK 25. Run `gradle clean build` or use the GitHub Actions workflow. Successful workflow runs publish a JAR artifact and a source ZIP.

See [configuration documentation](docs/CONFIGURATION.md), the [manual test checklist](docs/TEST_CHECKLIST.md), and the [Falix install guide](docs/FALIX_INSTALL.md).

## Release validation still required

The integrated feature set now compiles and the workflow publishes both the plugin JAR and source archive. A green build confirms compilation and packaging only; it does **not** prove runtime behavior on Purpur 26.3.

Before production use, run [the manual test checklist](docs/TEST_CHECKLIST.md) on a disposable Purpur 26.3 server, especially:
- GUI buy/sell confirmation, inventory-full handling, global stock and daily resets.
- Loan due time, 12-hour grace countdown, overdue purchase restrictions, and auto-repayment with both sufficient and insufficient funds.
- Death inventory and XP recovery through deaths, full inventories, expiry, and server restarts.
- Vault compatibility with the economy plugins you intend to run.
- Librarian trade restrictions, End-visit Elytra unlock, quest progress/event claims, and all utility commands.

No live Purpur server is attached to this workflow, so these runtime checks must be performed on your test server before calling this a production release.
