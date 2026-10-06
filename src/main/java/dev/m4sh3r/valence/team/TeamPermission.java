package dev.m4sh3r.valence.team;

import java.util.Locale;

public enum TeamPermission {
    INVITE,
    KICK,
    MANAGE_MEMBERS,
    DEPOSIT,
    UPGRADE,
    SET_HOME,
    USE_HOME,
    EDIT_SETTINGS,
    ENDER_CHEST,
    MANAGE_ALLIES,
    SET_WAYPOINT;

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static TeamPermission fromKey(String key) {
        try {
            return valueOf(key.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
