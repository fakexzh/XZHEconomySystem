package com.xzh.economy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Parsed representation of one objective from the approved quest grammar. */
record QuestObjectiveSpec(Type type, List<String> targets, int quantity, Dimension dimension) {
    enum Type { MINE, HARVEST, KILL, FISH, BREED, FEED, TAME, SMELT, CRAFT, ENCHANT, BARTER, LOOT, VAULT, TRAVEL, BIOME }
    enum Dimension { ANY, OVERWORLD, NETHER, END }

    static QuestObjectiveSpec parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Objective is blank");
        String[] parts = raw.trim().split(":", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Expected TYPE:TARGET:QTY[@DIMENSION]: " + raw);
        Type type;
        try { type = Type.valueOf(parts[0].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unknown objective type in " + raw); }

        String quantityAndDimension = parts[2];
        String[] dimensionParts = quantityAndDimension.split("@", -1);
        if (dimensionParts.length > 2) throw new IllegalArgumentException("Invalid dimension suffix in " + raw);
        int quantity;
        try { quantity = Integer.parseInt(dimensionParts[0]); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid objective quantity in " + raw); }
        if (quantity <= 0) throw new IllegalArgumentException("Objective quantity must be positive in " + raw);

        Dimension dimension = Dimension.ANY;
        if (dimensionParts.length == 2) {
            try { dimension = Dimension.valueOf(dimensionParts[1].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid objective dimension in " + raw); }
        }
        List<String> targets = Arrays.stream(parts[1].split("\\|", -1))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .toList();
        if (targets.isEmpty() || targets.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Objective target is empty in " + raw);
        }
        for (String target : targets) {
            if (!target.matches("(?:#[A-Z0-9_]+|[A-Z0-9_*]+)")) {
                throw new IllegalArgumentException("Invalid objective target '" + target + "' in " + raw);
            }
        }
        return new QuestObjectiveSpec(type, targets, quantity, dimension);
    }
}
