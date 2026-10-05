package dev.m4sh3r.valence.team;

import java.util.UUID;

public record Invite(UUID teamId, UUID inviter, String inviterName, long expiresAt) {

    public boolean expired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
