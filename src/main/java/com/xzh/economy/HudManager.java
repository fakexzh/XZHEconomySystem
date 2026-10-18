package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Criteria;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the player's economy sidebar and active-quest boss bar.
 * Loan and quest systems update this service; it does not invent quest progress.
 */
public final class HudManager {
    private final XZHEconomySystem plugin;
    private final Map<UUID, BossBar> questBars = new HashMap<>();
    private final Map<UUID, Double> loanAmounts = new HashMap<>();
    private final Map<UUID, Long> loanDueTimes = new HashMap<>();

    public HudManager(XZHEconomySystem plugin) {
        this.plugin = plugin;

    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) updateSidebar(player);
        }, 1L, 20L);
        for (Player player : Bukkit.getOnlinePlayers()) updateSidebar(player);
    }

    public void shutdown() {
        for (BossBar bar : questBars.values()) bar.removeAll();
        questBars.clear();
    }

    public void onJoin(Player player) {
        updateSidebar(player);
    }

    public void onQuit(Player player) {
        BossBar bar = questBars.remove(player.getUniqueId());
        if (bar != null) bar.removePlayer(player);
    }

    private void updateSidebar(Player player) {
        if (!plugin.getConfig().getBoolean("hud.scoreboard.enabled", true)) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("xzhhud", Criteria.DUMMY,
                ChatColor.GOLD + "" + ChatColor.BOLD + "XZH ECONOMY");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        UUID id = player.getUniqueId();
        double balance = plugin.getBalance(id);
        double owed = loanAmounts.getOrDefault(id, 0.0);
        long dueAt = loanDueTimes.getOrDefault(id, 0L);
        int line = 6;
        objective.getScore(ChatColor.GRAY + "Balance").setScore(line--);
        objective.getScore(ChatColor.YELLOW + formatMoney(balance)).setScore(line--);
        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(line--);
        objective.getScore(ChatColor.GRAY + "Loan owed").setScore(line--);
        objective.getScore((owed > 0 ? ChatColor.RED : ChatColor.GREEN) + formatMoney(owed)).setScore(line--);
        String time = owed <= 0 ? "No active loan" : remaining(dueAt);
        objective.getScore(ChatColor.AQUA + clip(time, 38)).setScore(line);
        player.setScoreboard(board);
    }

    /** Sets or updates a player's loan balance and repayment due time (epoch milliseconds). */
    public void setLoan(UUID playerId, double amountOwed, long dueAtEpochMillis) {
        if (amountOwed <= 0) {
            loanAmounts.remove(playerId);
            loanDueTimes.remove(playerId);
        } else {
            loanAmounts.put(playerId, amountOwed);
            loanDueTimes.put(playerId, dueAtEpochMillis);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) updateSidebar(player);
    }

    public double getLoanAmount(UUID playerId) {
        return loanAmounts.getOrDefault(playerId, 0.0);
    }

    public long getLoanDueAt(UUID playerId) {
        return loanDueTimes.getOrDefault(playerId, 0L);
    }

    /**
     * Shows the active quest HUD. Progress must be supplied by the quest system as 0..1.
     * Calling with progress < 0 or a blank quest name hides the bar.
     */
    public void showActiveQuest(Player player, String questName, String objectiveText, double progress) {
        if (questName == null || questName.isBlank() || progress < 0) {
            hideActiveQuest(player);
            return;
        }
        BossBar bar = questBars.computeIfAbsent(player.getUniqueId(), id ->
                Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID));
        bar.setTitle(ChatColor.GOLD + clip(questName, 64) + ChatColor.WHITE + " | "
                + clip(objectiveText == null ? "" : objectiveText, 80)
                + ChatColor.YELLOW + " (" + Math.round(clamp(progress) * 100) + "%)");
        bar.setProgress(clamp(progress));
        bar.setVisible(true);
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
    }

    public void hideActiveQuest(Player player) {
        BossBar bar = questBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private String formatMoney(double amount) {
        return String.format(java.util.Locale.US, "%.2f", Math.max(0, amount))
                .replaceAll("\\.00$", "");
    }

    private String remaining(long dueAt) {
        if (dueAt <= 0) return "Due time unset";
        long millis = dueAt - System.currentTimeMillis();
        if (millis <= 0) return "OVERDUE";
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        return days > 0 ? days + "d " + hours + "h left" :
                hours > 0 ? hours + "h " + minutes + "m left" : minutes + "m left";
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }

    private String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
