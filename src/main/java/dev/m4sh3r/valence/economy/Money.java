package dev.m4sh3r.valence.economy;

import org.bukkit.OfflinePlayer;

public interface Money {

    boolean enabled();

    double balance(OfflinePlayer player);

    boolean take(OfflinePlayer player, double amount);

    boolean give(OfflinePlayer player, double amount);

    Money NONE = new Money() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public double balance(OfflinePlayer player) {
            return 0;
        }

        @Override
        public boolean take(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public boolean give(OfflinePlayer player, double amount) {
            return false;
        }
    };
}
