package dev.m4sh3r.valence;

import dev.m4sh3r.valence.config.Settings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    static Settings settings;

    @BeforeAll
    static void load() {
        settings = loadBundled();
    }

    static Settings loadBundled() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(SettingsTest.class.getClassLoader().getResourceAsStream("config.yml")),
                StandardCharsets.UTF_8));
        Settings loaded = new Settings();
        loaded.load(config);
        return loaded;
    }

    @Test
    void readsLevelPerks() {
        assertEquals(5, settings.levels.size());
        assertEquals(0, settings.level(1).homes());
        assertEquals(1, settings.level(1).chestRows());
        assertEquals(6, settings.level(5).chestRows());
        assertEquals(3, settings.level(5).homes());
        assertEquals(14, settings.level(99).maxMembers());
    }

    @Test
    void findsNextLevel() {
        assertEquals(2, settings.nextLevel(1).level());
        assertNull(settings.nextLevel(5));
    }

    @Test
    void readsColorsAndOtherSections() {
        assertNotNull(settings.color("Aqua"));
        assertEquals("Purple", settings.colorName("purple"));
        assertTrue(settings.weeklyRewards.size() >= 3);
        assertEquals("file", settings.storageType);
        assertTrue(settings.prefixFormat.contains("<team_tag>"));
    }
}
