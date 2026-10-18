# Falix installation guide (development build)

1. Stop your server before changing plugin files.
2. Confirm the server software is Purpur 26.3 and that the host's selected Java runtime meets the server's requirement (this project compiles with Java 25).
3. Download the `XZHEconomySystem-1.0.0.jar` artifact from the GitHub Actions run for the `integrated-release` branch. Do not use an artifact from a failed workflow run.
4. Upload the JAR into the server's `plugins/` directory using the Falix file manager.
5. Start the server once and check the console for the plugin enable message. If it reports a Java version, API compatibility, or plugin-load error, stop and keep the log.
6. Stop the server and review `plugins/XZHEconomySystem/config.yml`. Adjust the values you need.
7. Start the server again. Do not edit the YAML files while the server is running.
8. Test on a backup/test server first. Check the console and `logs/latest.log` after testing.

## Current commands

- `/balance`, `/bal`, `/money`
- `/pay <player> <amount>`
- `/shop`, `/books`
- `/shopadmin restock` (operator/admin permission)
- `/shopadmin stock <MATERIAL> <amount>` (operator/admin permission)
- `/xzhadmin` (admin GUI; includes balance/price/stock tools and subsystem overviews)
- `/loan`, `/loan repay`
- `/quests`
- `/deathinventory`
- `/spawn`, `/sethome [name]`, `/home [name]`, `/delhome [name]`
- `/warp <name>`, `/tpa <player>`, `/tpaccept`, `/tpdeny`, `/back`
- `/msg <player> <message>`, `/reply <message>`, `/ignore <player>`
- `/afk`, `/near`, `/list`, `/top`, `/rtp`, `/kit starter`, `/help
- `/eco <set|add> <online-player> <amount>` (operator/admin permission)
- `/xzh`

## Before production use

This is a development branch. A successful GitHub Actions compile only verifies compilation; it does not prove that every GUI and gameplay flow works on a live Purpur server. Test purchases, stock persistence, loan due/grace handling, quest progress, death recovery, restarts, and backups before using it on a world players care about.
