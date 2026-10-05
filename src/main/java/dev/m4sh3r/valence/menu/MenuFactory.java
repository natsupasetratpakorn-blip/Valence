package dev.m4sh3r.valence.menu;

import dev.m4sh3r.valence.Valence;

public final class MenuFactory {

    private MenuFactory() {
    }

    public static boolean dialogsSupported() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.action.DialogActionCallback");
            Class.forName("net.kyori.adventure.dialog.DialogLike");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static Menus create(Valence plugin) {
        return dialogsSupported() ? dialogs(plugin) : new ChatMenus(plugin);
    }

    // Loaded by name so servers without the dialog API never touch those classes.
    private static Menus dialogs(Valence plugin) {
        try {
            return (Menus) Class.forName("dev.m4sh3r.valence.menu.dialog.DialogMenus")
                    .getConstructor(Valence.class)
                    .newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("Dialog menus could not start, using chat menus instead: " + e);
            return new ChatMenus(plugin);
        }
    }
}
