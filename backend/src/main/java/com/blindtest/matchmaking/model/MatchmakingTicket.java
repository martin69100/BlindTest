package com.blindtest.matchmaking.model;

import java.util.UUID;

public record MatchmakingTicket(
        UUID userId,
        String displayName,
        int elo,
        UUID preferredThemeId,
        long joinedAtEpochMs
) {
    public long getWaitTimeSeconds() {
        return (System.currentTimeMillis() - joinedAtEpochMs) / 1000;
    }

    public int getCurrentRange(int baseRange, int step, int stepSeconds, int maxRange) {
        long seconds = getWaitTimeSeconds();
        int expansion = (int) (seconds / stepSeconds) * step;
        return Math.min(baseRange + expansion, maxRange);
    }
}
