package com.blindtest.lobby.model;

import com.blindtest.track.entity.Track;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lobby {

    public enum LobbyStatus {
        WAITING,
        PLAYING,
        FINISHED
    }

    private String code;
    private UUID hostId;
    private String hostName;
    private UUID themeId;
    private String themeName;

    private String customPlaylistUrl;
    private String customPlaylistName;
    private String customPlaylistProvider;

    @JsonIgnore
    @Builder.Default
    private List<Track> customPlaylistTracks = new ArrayList<>();

    @Builder.Default
    private int roundsCount = 10;

    @Builder.Default
    private String gameMode = "BUZZER"; // "BUZZER" ou "NO_BUZZER"

    @Builder.Default
    private String teamMode = "INDIVIDUAL"; // "INDIVIDUAL" ou "TEAMS"

    @Builder.Default
    private LobbyStatus status = LobbyStatus.WAITING;

    private UUID activeGameId;

    @Builder.Default
    private Map<UUID, LobbyParticipant> participants = new ConcurrentHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> lastGameLeaderboard = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant lastActivityAt = Instant.now();

    public Collection<LobbyParticipant> getParticipantsList() {
        return participants.values();
    }
}
