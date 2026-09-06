package com.blindtest.game.model;

import com.blindtest.game.entity.MatchRound.GuessType;
import com.blindtest.track.entity.Track;
import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
@Builder
public class GameSession {

    public enum SessionState {
        WAITING_READY,
        PLAYING,
        BUZZED,
        BONUS_WINDOW,
        ROUND_REVEAL,
        FINISHED
    }

    private final UUID gameId;
    private final UUID player1Id;
    private final UUID player2Id; // null si mode solo
    private final String player1Name;
    private final String player2Name;
    private final UUID themeId;

    @Builder.Default
    private int player1Score = 0;

    @Builder.Default
    private int player2Score = 0;

    private List<Track> playlist;

    @Builder.Default
    private int currentRoundIndex = 0;

    @Builder.Default
    private SessionState state = SessionState.WAITING_READY;

    // État spécifique à la manche courante
    private UUID currentRoundId;
    private long roundStartTimestamp;
    private long roundRemainingDurationMs; // utile pour la reprise après mauvais buzz (Option A)

    private UUID currentBuzzerPlayerId;
    private long buzzTimestamp;

    @Builder.Default
    private boolean titleFound = false;

    @Builder.Default
    private boolean artistFound = false;

    @Builder.Default
    private GuessType firstFoundType = GuessType.NONE;

    // Joueurs prêts au démarrage de la partie
    @Builder.Default
    private Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();

    // Option A : Vol de main
    @Builder.Default
    private Set<UUID> playersBuzzedInRound = ConcurrentHashMap.newKeySet();

    // Verrou atomique pour concurrence sur le buzzer
    @Builder.Default
    private AtomicBoolean buzzLock = new AtomicBoolean(false);

    // Tâche planifiée courante (timeout d'écoute 20s, saisie 5s, bonus 10s, auto-next 5s)
    private ScheduledFuture<?> scheduledTask;

    public synchronized void cancelScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    public synchronized void setScheduledTask(ScheduledFuture<?> task) {
        cancelScheduledTask();
        this.scheduledTask = task;
    }

    public Track getCurrentTrack() {
        if (playlist == null || currentRoundIndex < 0 || currentRoundIndex >= playlist.size()) {
            return null;
        }
        return playlist.get(currentRoundIndex);
    }

    public boolean isSolo() {
        return player2Id == null;
    }

    public boolean hasMoreRounds() {
        return playlist != null && (currentRoundIndex + 1) < playlist.size();
    }

    public void nextRound() {
        cancelScheduledTask();
        this.currentRoundIndex++;
        this.currentRoundId = UUID.randomUUID();
        this.titleFound = false;
        this.artistFound = false;
        this.firstFoundType = GuessType.NONE;
        this.currentBuzzerPlayerId = null;
        this.playersBuzzedInRound.clear();
        this.buzzLock.set(false);
    }

    public void addScore(UUID playerId, int points) {
        if (playerId.equals(player1Id)) {
            player1Score += points;
        } else if (playerId.equals(player2Id)) {
            player2Score += points;
        }
    }
}
