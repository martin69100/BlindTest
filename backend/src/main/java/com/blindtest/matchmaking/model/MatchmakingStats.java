package com.blindtest.matchmaking.model;

public record MatchmakingStats(
        int inQueue,
        int inGame,
        int activeMatches
) {}
