package com.blindtest.lobby.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CustomGameFinishedEvent {
    private final String lobbyCode;
    private final UUID gameId;
    private final Map<UUID, Integer> finalScores;
    private final List<Map<String, Object>> leaderboard;
}
