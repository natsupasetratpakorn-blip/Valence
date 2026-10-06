package dev.m4sh3r.valence;

import dev.m4sh3r.valence.config.Settings;
import dev.m4sh3r.valence.storage.FileBackend;
import dev.m4sh3r.valence.storage.TeamStorage;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamHome;
import dev.m4sh3r.valence.team.TeamMember;
import dev.m4sh3r.valence.team.TeamPermission;
import dev.m4sh3r.valence.team.TeamRank;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamStorageTest {

    @TempDir
    Path folder;

    private TeamStorage storage() {
        Settings settings = SettingsTest.loadBundled();
        return new TeamStorage(null, settings, new FileBackend(folder.toFile()));
    }

    private Team sample() {
        UUID owner = UUID.randomUUID();
        Team team = new Team(UUID.randomUUID(), "Valence", "VAL", "Purple", Material.AMETHYST_SHARD, owner, 1000L);
        TeamStorage.ensureBuiltInRanks(team);
        TeamRank officer = new TeamRank("r1", "Officer", Material.IRON_HELMET, 50);
        officer.permissions().add(TeamPermission.KICK);
        team.ranks().put(officer.id(), officer);
        team.members().put(owner, new TeamMember(owner, "M4sh3r", TeamRank.OWNER, 1000L));
        UUID steve = UUID.randomUUID();
        TeamMember member = new TeamMember(steve, "Steve", "r1", 2000L);
        member.kills(4);
        member.friendlyFire(true);
        team.members().put(steve, member);
        team.bank(12500.5);
        team.level(3);
        team.home(2, new TeamHome("world", 1.5, 64, -3, 90f, 0f));
        team.waypoint(new TeamHome("world_nether", 10, 70, 10, 0f, 0f), "Fortress");
        team.allies().add(UUID.randomUUID());
        team.chest(List.of("", "abc"));
        return team;
    }

    @Test
    void roundTripKeepsEverything() {
        TeamStorage storage = storage();
        Team team = sample();
        Team copy = storage.parse(storage.snapshot(team));
        assertNotNull(copy);
        assertEquals("Valence", copy.name());
        assertEquals(12500.5, copy.bank());
        assertEquals(3, copy.level());
        assertEquals(2, copy.members().size());
        TeamMember steve = copy.members().values().stream().filter(m -> m.name().equals("Steve")).findFirst().orElseThrow();
        assertEquals(4, steve.kills());
        assertTrue(steve.friendlyFire());
        assertEquals("Officer", copy.rankOf(steve.uuid()).name());
        assertTrue(copy.rank("r1").has(TeamPermission.KICK));
        assertEquals(1.5, copy.home(2).x());
        assertEquals("Fortress", copy.waypointName());
        assertEquals(team.allies(), copy.allies());
        assertEquals(List.of("", "abc"), copy.chest());
        assertFalse(copy.dirty());
    }

    @Test
    void olderSaveNeverReplacesNewerOne() throws Exception {
        TeamStorage storage = storage();
        Team team = sample();
        long older = storage.nextVersion();
        long newer = storage.nextVersion();
        team.bank(1);
        String first = storage.snapshot(team);
        team.bank(2);
        String second = storage.snapshot(team);
        storage.write(team.id(), second, newer);
        storage.write(team.id(), first, older);
        Team saved = storage.parse(Files.readString(folder.resolve(team.id() + ".yml")));
        assertEquals(2.0, saved.bank());
    }

    @Test
    void deletedTeamIsNeverWrittenAgain() {
        TeamStorage storage = storage();
        Team team = sample();
        long version = storage.nextVersion();
        storage.delete(team.id());
        storage.write(team.id(), storage.snapshot(team), version);
        assertFalse(Files.exists(folder.resolve(team.id() + ".yml")));
    }

    @Test
    void oldFilesGetTheEnderChestPermission() {
        TeamStorage storage = storage();
        Team team = sample();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(storage.snapshot(team));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        yaml.set("data-version", 1);
        yaml.set("ranks.r1.permissions", List.of("kick"));
        Team loaded = storage.parse(yaml.saveToString());
        assertTrue(loaded.rank("r1").has(TeamPermission.ENDER_CHEST));
        assertTrue(loaded.dirty());
    }
}
