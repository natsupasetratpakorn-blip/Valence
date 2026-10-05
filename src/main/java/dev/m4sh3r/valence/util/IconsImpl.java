package dev.m4sh3r.valence.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.util.Locale;
import java.util.UUID;

// Only loaded through Icons once the object component classes are known to exist.
final class IconsImpl {

    private static final Key ITEMS = Key.key("minecraft", "items");
    private static final Key BLOCKS = Key.key("minecraft", "blocks");

    private IconsImpl() {
    }

    static Component sprite(String name) {
        String clean = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (clean.startsWith("block/")) {
            return Component.object(ObjectContents.sprite(BLOCKS, Key.key("minecraft", clean)));
        }
        if (!clean.startsWith("item/")) {
            clean = "item/" + clean;
        }
        return Component.object(ObjectContents.sprite(ITEMS, Key.key("minecraft", clean)));
    }

    static Component head(UUID uuid, String name, String texture, String signature) {
        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead().id(uuid).name(name).hat(true);
        if (texture != null) {
            builder.profileProperty(PlayerHeadObjectContents.property("textures", texture, signature));
        }
        return Component.object(builder.build());
    }
}
