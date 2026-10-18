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
    private final Map<UUID, Long> loanGraceTimes = new HashMap<>();
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private final Map<UUID, java.util.List<String>> sidebarLines = new HashMap<>();

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
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) player.setScoreboard(manager.getMainScoreboard());
        }
        scoreboards.clear();
        sidebarLines.clear();
    }

    public void onJoin(Player player) {
        updateSidebar(player);
    }

    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        BossBar bar = questBars.remove(id);
        if (bar != null) bar.removePlayer(player);
        scoreboards.remove(id);
        sidebarLines.remove(id);
    }

    private void updateSidebar(Player player) {
        UUID id = player.getUniqueId();
        if (!plugin.getConfig().getBoolean("hud.scoreboard.enabled", true)) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) player.setScoreboard(manager.getMainScoreboard());
            scoreboards.remove(id);
            sidebarLines.remove(id);
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = scoreboards.computeIfAbsent(id, ignored -> manager.getNewScoreboard());
        Objective objective = board.getObjective("xzhhud");
        if (objective == null) {
            objective = board.registerNewObjective("xzhhud", Criteria.DUMMY,
                    ChatColor.GOLD + "" + ChatColor.BOLD + "XZH ECONOMY");
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (String oldLine : sidebarLines.getOrDefault(id, java.util.List.of())) board.resetScores(oldLine);
        double balance = plugin.getBalance(id);
        double owed = loanAmounts.getOrDefault(id, 0.0);
        long dueAt = loanDueTimes.getOrDefault(id, 0L);
        long graceUntil = loanGraceTimes.getOrDefault(id, 0L);
        java.util.List<String> lines = java.util.List.of(
                ChatColor.GRAY + "Balance",
                ChatColor.YELLOW + formatMoney(balance),
                ChatColor.DARK_GRAY + " ",
                ChatColor.GRAY + "Loan owed",
                (owed > 0 ? ChatColor.RED : ChatColor.GREEN) + formatMoney(owed),
                ChatColor.AQUA + clip(owed <= 0 ? "No active loan" : remaining(dueAt, graceUntil), 38)
        );
        int score = lines.size();
        for (String line : lines) objective.getScore(line).setScore(score--);
        sidebarLines.put(id, lines);
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    /** Backward-compatible loan HUD update; assumes the standard 12-hour grace period. */
    public void setLoan(UUID playerId, double amountOwed, long dueAtEpochMillis) {
        setLoan(playerId, amountOwed, dueAtEpochMillis,
                amountOwed > 0 && dueAtEpochMillis > 0 ? dueAtEpochMillis + 12L * 60L * 60L * 1000L : 0L);
    }

    /** Sets loan amount, due time, and grace expiry (epoch milliseconds). */
    public void setLoan(UUID playerId, double amountOwed, long dueAtEpochMillis, long graceUntilEpochMillis) {
        if (!Double.isFinite(amountOwed) || amountOwed <= 0) {
            loanAmounts.remove(playerId);
            loanDueTimes.remove(playerId);
            loanGraceTimes.remove(playerId);
        } else {
            loanAmounts.put(playerId, amountOwed);
            loanDueTimes.put(playerId, dueAtEpochMillis);
            loanGraceTimes.put(playerId, graceUntilEpochMillis);
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
        if (!plugin.getConfig().getBoolean("hud.quest-bossbar.enabled", true)) {
            hideActiveQuest(player);
            return;
        }
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

    private String remaining(long dueAt, long graceUntil) {
        long now = System.currentTimeMillis();
        if (dueAt <= 0) return "Due time unset";
        if (now >= graceUntil && graceUntil > 0) return "GRACE ENDED";
        boolean overdue = now >= dueAt;
        long millis = (overdue && graceUntil > 0 ? graceUntil : dueAt) - now;
        Duration d = Duration.ofMillis(Math.max(0L, millis));
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        String time = days > 0 ? days + "d " + hours + "h" :
                hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        return overdue ? "OVERDUE · auto in " + time : time + " left";
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }

    private String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
