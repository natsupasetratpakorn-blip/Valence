package dev.m4sh3r.valence.team;

import java.util.UUID;

public final class TeamMember {

    private final UUID uuid;
    private String name;
    private String rankId;
    private final long joinedAt;
    private int kills;
    private int deaths;
    private double deposited;
    private boolean friendlyFire;
    private String skin;
    private String skinSignature;

    public TeamMember(UUID uuid, String name, String rankId, long joinedAt) {
        this.uuid = uuid;
        this.name = name;
        this.rankId = rankId;
        this.joinedAt = joinedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String rankId() {
        return rankId;
    }

    public void rankId(String rankId) {
        this.rankId = rankId;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public int kills() {
        return kills;
    }

    public void kills(int kills) {
        this.kills = kills;
    }

    public int deaths() {
        return deaths;
    }

    public void deaths(int deaths) {
        this.deaths = deaths;
    }

    public double deposited() {
        return deposited;
    }

    public void deposited(double deposited) {
        this.deposited = deposited;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public void friendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public String skin() {
        return skin;
    }

    public String skinSignature() {
        return skinSignature;
    }

    public void skin(String skin, String signature) {
        this.skin = skin;
        this.skinSignature = signature;
    }
}
