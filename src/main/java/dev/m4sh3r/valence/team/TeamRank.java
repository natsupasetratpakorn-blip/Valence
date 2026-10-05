package dev.m4sh3r.valence.team;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public final class TeamRank {

    public static final String OWNER = "owner";
    public static final String MEMBER = "member";
    public static final int OWNER_WEIGHT = 100;
    public static final int MEMBER_WEIGHT = 0;

    private final String id;
    private String name;
    private Material icon;
    private int weight;
    private final Set<TeamPermission> permissions = EnumSet.noneOf(TeamPermission.class);

    public TeamRank(String id, String name, Material icon, int weight) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public Material icon() {
        return icon;
    }

    public void icon(Material icon) {
        this.icon = icon;
    }

    public int weight() {
        return weight;
    }

    public void weight(int weight) {
        this.weight = weight;
    }

    public Set<TeamPermission> permissions() {
        return permissions;
    }

    public boolean isOwner() {
        return id.equals(OWNER);
    }

    public boolean isDefault() {
        return id.equals(MEMBER);
    }

    public boolean builtIn() {
        return isOwner() || isDefault();
    }

    public boolean has(TeamPermission permission) {
        return isOwner() || permissions.contains(permission);
    }
}
