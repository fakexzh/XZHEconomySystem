package com.xzh.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Optional Vault bridge. Banks and world-specific balances are intentionally unsupported. */
@SuppressWarnings("deprecation")
public final class VaultEconomyProvider implements Economy {
    private final XZHEconomySystem plugin;

    public VaultEconomyProvider(XZHEconomySystem plugin) { this.plugin = plugin; }

    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public String getName() { return "XZHEconomySystem"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return String.format(Locale.US, "%.2f %s", amount, currencyNamePlural()); }
    @Override public String currencyNamePlural() { return plugin.getConfig().getString("currency-name", "coins"); }
    @Override public String currencyNameSingular() { return plugin.getConfig().getString("currency-name-singular", "coin"); }

    @Override public boolean hasAccount(String playerName) { return hasAccount(Bukkit.getOfflinePlayer(playerName)); }
    @Override public boolean hasAccount(OfflinePlayer player) { return plugin.hasBalanceAccount(player.getUniqueId()); }
    @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }

    @Override public double getBalance(String playerName) { return getBalance(Bukkit.getOfflinePlayer(playerName)); }
    @Override public double getBalance(OfflinePlayer player) { return plugin.getBalance(player.getUniqueId()); }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(String playerName, double amount) { return has(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public boolean has(OfflinePlayer player, double amount) { return validAmount(amount) && getBalance(player) >= amount; }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (!validAmount(amount)) return response(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be finite and non-negative.");
        double balance = getBalance(player);
        if (balance < amount) return response(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
        plugin.setBalance(player.getUniqueId(), balance - amount);
        return response(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }

    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (!validAmount(amount)) return response(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be finite and non-negative.");
        double balance = getBalance(player);
        plugin.setBalance(player.getUniqueId(), balance + amount);
        return response(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

    @Override public EconomyResponse createBank(String name, String player) { return notImplemented(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public EconomyResponse deleteBank(String name) { return notImplemented(); }
    @Override public EconomyResponse bankBalance(String name) { return notImplemented(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public List<String> getBanks() { return Collections.emptyList(); }

    @Override public boolean createPlayerAccount(String playerName) { return createPlayerAccount(Bukkit.getOfflinePlayer(playerName)); }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { plugin.getBalance(player.getUniqueId()); return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return createPlayerAccount(playerName); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return createPlayerAccount(player); }

    private boolean validAmount(double amount) { return Double.isFinite(amount) && amount >= 0; }
    private EconomyResponse response(double amount, double balance, EconomyResponse.ResponseType type, String error) {
        return new EconomyResponse(amount, balance, type, error);
    }
    private EconomyResponse notImplemented() {
        return response(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts are not supported.");
    }
}
