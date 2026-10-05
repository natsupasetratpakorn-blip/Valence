package dev.m4sh3r.valence.util;

import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Small item and head pictures inside text. Clients and servers older than 1.21.9 can't show them,
 * so there they turn into nothing instead of breaking the text.
 */
public final class Icons {

    public static final boolean SUPPORTED = supported();

    private Icons() {
    }

    public static Component item(String name) {
        return SUPPORTED ? IconsImpl.sprite(name) : Component.empty();
    }

    public static Component head(UUID uuid, String name, String texture, String signature) {
        return SUPPORTED ? IconsImpl.head(uuid, name, texture, signature) : Component.empty();
    }

    private static boolean supported() {
        try {
            Class.forName("net.kyori.adventure.text.object.ObjectContents");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
