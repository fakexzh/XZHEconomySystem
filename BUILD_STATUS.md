# XZHEconomySystem — Fix Draft Status

**Status: source draft only. Not a release JAR.**

The source draft contains the current reported fixes and additions. It has not been compiled against the Purpur/Paper 26.3 API in this environment because only Java 21 is installed, the project requires Java 25, Gradle is not installed, and downloading build tools is unavailable here.

## Included in this draft
- Admin utility commands restricted by `xzh.admin`: `/gmc`, `/gms`, `/gma`, `/gmsp`, `/gamemode`, `/day`, `/night`, `/heal`, `/feed`, `/fly`, `/weather`.
- In-game world clock in the action bar, configurable under `hud.clock`.
- Bed-based `/home`: place a bed to set it, and breaking either half clears that bed home; named homes remain available by name.
- Full potion-type listings for regular, splash, and lingering potions, with separate variant pricing and variant-aware selling.
- XP bottle shop entry retained.
- Revised quest pack objectives and restricted global stock for several rare items.
- Netherite Upgrade template is buyable; all smithing templates and selected exploit-prone rare items are blocked from selling.
- `/pay` spends pocket coins first and bank funds second; existing bank and loan code was left intact.
- Custom XZH coin resource-pack assets and CustomModelData assignment.
- Very cheap default sell value for unfeatured items; values under 1 coin total cannot be sold.

## Before release
1. Build using JDK 25 and Gradle (`gradle clean build`).
2. Fix any compile errors against the exact Purpur 26.3 API.
3. Install the JAR on a disposable Purpur 26.3 server and complete `docs/TEST_CHECKLIST.md`.
4. Validate `resource-pack/` on a matching client version; `pack_format` is marked as a candidate until checked in-game.
5. Only then publish a release JAR.
