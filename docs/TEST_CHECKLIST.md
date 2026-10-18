# Manual Purpur 26.3 test checklist

Run these on a disposable test world with a backup. Record any console error and the exact action that caused it.

## Startup and persistence
- [ ] Start server on Java 25 / Purpur 26.3 and confirm the plugin enables without warnings/errors.
- [ ] Run `/balance`, stop/restart, and confirm the balance persists.
- [ ] Make a balance change and verify `plugins/XZHEconomySystem/transactions.log` records it.
- [ ] Verify `/pay` rejects zero, negative, non-finite, and over-limit amounts; verify invalid negative/NaN configured shop and book prices fall back safely.

## Shop
- [ ] Open `/shop`; verify category navigation and Back buttons.
- [ ] Verify expanded Armor & Tools, cooked/raw food, heads, and friendly spawn egg catalog; confirm rare items use configured global stock caps.
- [ ] Try dragging items into each economy GUI; the drag must be cancelled and no item may be deposited or lost.
- [ ] Buy 1/10/32/64 of a featured item; verify confirmation, balance deduction, inventory capacity handling, and global stock decrease.
- [ ] Cancel a purchase and confirm no balance/stock change.
- [ ] Sell a featured item; verify 30% sellback.
- [ ] Hold an unfeatured item and use Sell Hand; confirm value is shown before the sale and stacks worth less than 1 coin are rejected.
- [ ] Verify passive spawn eggs are buy-only, the chicken egg can be sold, and rare item stock resets on the next server-local day.
- [ ] Try to buy Elytra before and after entering the End.
- [ ] Buy an enchantment book; verify the correct stored enchantment, 24-hour personal cooldown, and global daily stock cap.

## Bank
- `/bank` opens the separate `XZH BANK` GUI.
- Verify the clock uses the configured bank world and deposit/withdrawal are permitted at 09:00 but blocked at 20:00 Minecraft time.
- Verify pocket currency appears as tagged XZH coin items in 1/10/100/1000 denominations; normal gold items must not count as coins.
- Verify coins can be moved/traded, cannot be crafted or placed as blocks, and drop on overworld death without also being recoverable through the paid death snapshot.
- Verify legacy balances migrate to physical coins once, with no duplicate starting balance after reconnect/restart.
- Verify deposits move funds from pocket coin items to bank and withdrawals create coin items without changing the combined total.
- Verify shop purchases and manual/automatic loan repayments can use bank funds even while the bank is closed.
- Verify insufficient combined funds causes no partial charge and bank balances persist after a restart.
- Verify the Loans button opens the loan GUI and loan confirmations still work from that menu.

## Money requests and global announcements
- [ ] Use `/sendmoney <player> 500`; recipient gets an accept/decline GUI, and accepting transfers funds only once.
- [ ] Use `/askmoney <player> 500`; target gets an accept/decline GUI and accepting pays the requester.
- [ ] Verify decline, timeout, offline player, insufficient funds, and repeated pending requests do not move coins.
- [ ] Buy an inexpensive common item and confirm no global shop announcement.
- [ ] Buy an expensive or configured rare item and confirm the XZH SHOP announcement appears to all online players.
- [ ] Buy Elytra after the End-visit requirement and confirm the progression announcement.
- [ ] Borrow and fully repay a loan manually and automatically; confirm global XZH BANK announcements.

## Loans and HUD
- [ ] Take each loan amount/term on a disposable player; verify displayed total repayment and due time.
- [ ] Repay early through the loan GUI and `/loan repay`.
- [ ] With a test loan, verify overdue shop purchases are blocked while selling/quest progress still works.
- [ ] Test auto-repayment at grace expiry with sufficient and insufficient balances.
- [ ] Confirm sidebar shows balance, amount owed, and due time; during the 12-hour grace period it should show the auto-repayment countdown; after grace it should show grace ended. Confirm active quest boss bar displays objective and percentage.

## Quests and events
- [ ] Buy each quest pack and confirm global stock decreases.
- [ ] Complete block, ore, mob, Nether, End, and travel objectives.
- [ ] In Creative and Spectator modes, verify block breaks, kills, and travel do not advance paid quests or the global ore event.
- [ ] Verify completion reward, boss-bar removal, and pack stock reset on a new day.
- [ ] Mine ores toward the global event; verify shared progress, personal contribution requirement, claim stock, and one claim per day.
- [ ] Change `quests.global-event` goal/reward/claims and `quests.pack-stock` values, reload, and verify the next daily reset uses the configured defaults.
- [ ] Confirm reminder timing is 30 minutes.

## Death recovery and world rules
- [ ] In the Overworld, die with items and XP; verify drops are withheld, penalty is 10% of estimated value (min 10/max 1000, capped by balance), and recovery works within 30 minutes.
- [ ] Verify recovery fee, item delivery when inventory is full, XP restoration, expiry, and two-successful-recoveries-per-day limit.
- [ ] Die in Nether and End; verify inventory and XP are kept when `death-recovery.overworld-only: true`.

## Utilities and administration
- [ ] Test spawn, homes, warps, TPA accept/deny/expiry, back, private messages, ignore, AFK, near, list, top, and RTP safety/cooldown.
- [ ] Open `/xzhadmin`; test balance edits, price edits, stock edits/restock, transaction history, diagnostics, book cooldown reset, active quest reset, quest pack stock control, loan/recovery overrides, global event reset, and config reload.
- [ ] Verify librarian enchanted-book trades are unavailable by default; when the config toggle is false, newly generated offers may include books (previously removed offers may not return).
- [ ] Verify the End-visit Elytra lock and buy-only spawn egg restriction follow their config toggles.
- [ ] Restart the server and verify all persistent data remains intact.

A green GitHub Actions build verifies compilation only. Do not use this as a production release until the checklist has been completed on a real Purpur 26.3 server.
