XZHEconomySystem custom pocket coin resource pack for Minecraft Java 26.3.
Install this pack on every client to see the XZH coin art. The plugin sets legacy CustomModelData to the coin denomination (1, 10, 100, 1000).
The manifest declares resource-pack format 97.1 for Java 26.3. The pack still needs a live-client visual check.


XZH Premium Boats (in progress)
The premium boat shop defines three tiers: Basic (oak boat), Aura (birch boat), and Super (dark oak boat), each with a chest variant. The plugin tags purchased items and placed boats with persistent tier identifiers. The intended visual mapping is Basic = natural wood / black XZH, Aura = white / black XZH, Super = black / white XZH.

Important: entity texture atlases still need their final pixel-art textures and a live-client resource-pack validation before the boat artwork can be considered complete.


XZH GUI icon pack (assets/xzh/textures/gui)
Menu buttons use minecraft:item_model = xzh:gui/<icon> (set by the plugin's GuiIcons class). Slots, names, lore and click
actions are unchanged; only the icon art differs. Set gui-icons.enabled: false in config.yml to show vanilla items again.
Native art is 16x16 pixel art. Money-request icons (coin_send / coin_question / coin_accept / coin_decline) are in the pack
for future use: money requests are currently chat-based and have no GUI.
