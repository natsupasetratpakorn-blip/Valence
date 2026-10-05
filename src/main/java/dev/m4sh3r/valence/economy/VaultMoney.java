package dev.m4sh3r.valence.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultMoney implements Money {

    private final Economy economy;

    private VaultMoney(Economy economy) {
        this.economy = economy;
    }

    public static Money hook() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) {
            return null;
        }
        return new VaultMoney(provider.getProvider());
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public double balance(OfflinePlayer player) {
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean take(OfflinePlayer player, double amount) {
        if (!economy.has(player, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean give(OfflinePlayer player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
