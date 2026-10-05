package dev.m4sh3r.valence.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class Team {

    private final UUID id;
    private String name;
    private String tag;
    private String color;
    private Material icon;
    private UUID owner;
    private final long createdAt;
    private int level = 1;
    private double bank;
    private boolean open;
    private TeamHome home;
    private List<String> chest = List.of();
    private final Map<UUID, TeamMember> members = new ConcurrentHashMap<>();
    private final Map<String, TeamRank> ranks = new ConcurrentHashMap<>();
    private final Deque<LogEntry> log = new ConcurrentLinkedDeque<>();
    private volatile boolean dirty;
    private TextColor resolvedColor;

    public Team(UUID id, String name, String tag, String color, Material icon, UUID owner, long createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.color = color;
        this.icon = icon;
        this.owner = owner;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String tag() {
        return tag;
    }

    public void tag(String tag) {
        this.tag = tag;
    }

    public String color() {
        return color;
    }

    public void color(String color, TextColor resolved) {
        this.color = color;
        this.resolvedColor = resolved;
    }

    public TextColor textColor() {
        return resolvedColor;
    }

    public Component displayName() {
        return Component.text(name, resolvedColor);
    }

    public Material icon() {
        return icon;
    }

    public void icon(Material icon) {
        this.icon = icon;
    }

    public UUID owner() {
        return owner;
    }

    public void owner(UUID owner) {
        this.owner = owner;
    }

    public long createdAt() {
        return createdAt;
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = level;
    }

    public double bank() {
        return bank;
    }

    public void bank(double bank) {
        this.bank = Math.max(0, Math.round(bank * 100.0) / 100.0);
    }

    public boolean open() {
        return open;
    }

    public void open(boolean open) {
        this.open = open;
    }

    public TeamHome home() {
        return home;
    }

    public void home(TeamHome home) {
        this.home = home;
    }

    public List<String> chest() {
        return chest;
    }

    public void chest(List<String> chest) {
        this.chest = List.copyOf(chest);
    }

    public Map<UUID, TeamMember> members() {
        return members;
    }

    public TeamMember member(UUID uuid) {
        return members.get(uuid);
    }

    public Map<String, TeamRank> ranks() {
        return ranks;
    }

    public TeamRank rank(String id) {
        return ranks.get(id);
    }

    public TeamRank rankOf(UUID uuid) {
        TeamMember member = members.get(uuid);
        if (member == null) {
            return null;
        }
        if (uuid.equals(owner)) {
            return ranks.get(TeamRank.OWNER);
        }
        TeamRank rank = ranks.get(member.rankId());
        return rank != null ? rank : ranks.get(TeamRank.MEMBER);
    }

    public int weightOf(UUID uuid) {
        TeamRank rank = rankOf(uuid);
        return rank == null ? -1 : rank.weight();
    }

    public boolean has(UUID uuid, TeamPermission permission) {
        TeamRank rank = rankOf(uuid);
        return rank != null && rank.has(permission);
    }

    public List<TeamRank> sortedRanks() {
        List<TeamRank> list = new ArrayList<>(ranks.values());
        list.sort(Comparator.comparingInt((TeamRank r) -> r.weight()).reversed().thenComparing(r -> r.name()));
        return list;
    }

    public List<TeamMember> sortedMembers() {
        List<TeamMember> list = new ArrayList<>(members.values());
        list.sort(Comparator.<TeamMember>comparingInt(m -> -weightOf(m.uuid()))
                .thenComparing(m -> Bukkit.getPlayer(m.uuid()) == null)
                .thenComparing(m -> m.name().toLowerCase()));
        return list;
    }

    public int onlineCount() {
        int count = 0;
        for (UUID uuid : members.keySet()) {
            if (Bukkit.getPlayer(uuid) != null) {
                count++;
            }
        }
        return count;
    }

    public Deque<LogEntry> log() {
        return log;
    }

    public void log(String text, int max) {
        log.addFirst(new LogEntry(System.currentTimeMillis(), text));
        while (log.size() > max) {
            log.removeLast();
        }
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clean() {
        dirty = false;
    }
}
