package dev.m4sh3r.valence.config;

import dev.m4sh3r.valence.util.Icons;
import dev.m4sh3r.valence.util.SmallCaps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern NO_ICONS = Pattern.compile("<(icon:[^>]*|head|rank_icon)>\\s?");

    private final Plugin plugin;
    private final Settings settings;
    private YamlConfiguration yaml;
    private TagResolver palette = TagResolver.empty();
    private String prefix = "";

    public Messages(Plugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }

        List<TagResolver> colors = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("palette");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                TextColor color = TextColor.fromHexString(section.getString(key, ""));
                if (color != null) {
                    colors.add(Placeholder.styling(key, color));
                }
            }
        }
        colors.add(TagResolver.resolver("icon", (args, context) ->
                Tag.selfClosingInserting(Icons.item(args.popOr("icon needs an item name").value()))));
        palette = TagResolver.resolver(colors);
        prefix = yaml.getString("prefix", "");
    }

    public List<TextColor> listColors() {
        List<TextColor> list = new ArrayList<>();
        for (String hex : yaml.getStringList("menu.list-colors")) {
            TextColor color = TextColor.fromHexString(hex);
            if (color != null) {
                list.add(color);
            }
        }
        if (list.isEmpty()) {
            list.add(TextColor.color(0xD6D9DE));
        }
        return list;
    }

    public TagResolver palette() {
        return palette;
    }

    public String raw(String key) {
        // getString(key, fallback) skips the bundled defaults, so new keys would vanish after an update.
        String value = yaml.getString(key);
        return value != null ? value : key;
    }

    /**
     * Text for menus. Italic is turned off since dialogs default to it in some places.
     */
    public Component menu(String key, TagResolver... resolvers) {
        return parse(raw("menu." + key), resolvers);
    }

    /**
     * Dialog titles are written in small caps, including names placed inside them.
     */
    public Component title(String key, TagResolver... resolvers) {
        return SmallCaps.component(menu(key, resolvers));
    }

    public String menuRaw(String key) {
        return raw("menu." + key);
    }

    public Component parse(String text, TagResolver... resolvers) {
        String clean = text.replace(SmallCaps.KEEP_OPEN, "").replace(SmallCaps.KEEP_CLOSE, "");
        if (!Icons.SUPPORTED) {
            clean = NO_ICONS.matcher(clean).replaceAll("");
        }
        return MINI.deserialize(clean, TagResolver.resolver(palette, TagResolver.resolver(resolvers)))
                .decoration(TextDecoration.ITALIC, false);
    }

    public Component chat(String key, TagResolver... resolvers) {
        return chatText(raw("chat." + key), resolvers);
    }

    private Component chatText(String text, TagResolver... resolvers) {
        String full = prefix + text;
        if (settings.smallCaps) {
            full = SmallCaps.convertMiniMessage(full);
        }
        return parse(full, resolvers);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(chat(key, resolvers));
    }

    public void sendList(CommandSender to, String key) {
        for (String line : yaml.getStringList("chat." + key)) {
            String text = settings.smallCaps ? SmallCaps.convertMiniMessage(line) : line;
            to.sendMessage(parse(text));
        }
    }

    public void sendRaw(CommandSender to, String text, TagResolver... resolvers) {
        to.sendMessage(chatText(text, resolvers));
    }

    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
