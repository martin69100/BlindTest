package com.blindtest.lobby.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LobbyParticipant {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private int elo;
    private boolean isHost;
    private boolean isReady;
    private int lastGameScore;
    private int lastGameRank;
    @Builder.Default
    private String team = "BLUE"; // "BLUE" ou "RED"
}
