package dev.m4sh3r.valence.util;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;

public final class SmallCaps {

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {
    }

    public static String convert(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = NORMAL.indexOf(Character.toLowerCase(c));
            out.append(index >= 0 ? SMALL.charAt(index) : c);
        }
        return out.toString();
    }

    public static final String KEEP_OPEN = "<nosc>";
    public static final String KEEP_CLOSE = "</nosc>";

    /**
     * Converts the visible text of a MiniMessage string and leaves its tags alone.
     * Anything between nosc tags, like commands, keeps the normal font.
     */
    public static String convertMiniMessage(String input) {
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            if (input.startsWith(KEEP_OPEN, i)) {
                int end = input.indexOf(KEEP_CLOSE, i);
                int stop = end == -1 ? input.length() : end + KEEP_CLOSE.length();
                out.append(input, i, stop);
                i = stop;
                continue;
            }
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                out.append(c).append(input.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '<') {
                int end = input.indexOf('>', i);
                if (end != -1) {
                    out.append(input, i, end + 1);
                    i = end + 1;
                    continue;
                }
            }
            int next = nextSpecial(input, i);
            out.append(convert(input.substring(i, next)));
            i = next;
        }
        return out.toString();
    }

    private static int nextSpecial(String input, int from) {
        for (int i = from; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '<' || c == '\\') {
                return i == from ? i + 1 : i;
            }
        }
        return input.length();
    }

    /**
     * Converts all plain text in an already built component, placeholders included.
     */
    public static Component component(Component component) {
        Component result = component instanceof TextComponent text ? text.content(convert(text.content())) : component;
        if (result.children().isEmpty()) {
            return result;
        }
        List<Component> children = new ArrayList<>(result.children().size());
        for (Component child : result.children()) {
            children.add(component(child));
        }
        return result.children(children);
    }
}
