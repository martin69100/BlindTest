package com.blindtest.lobby.service;

import com.blindtest.game.model.GameSession;
import com.blindtest.game.service.GameEngineService;
import com.blindtest.lobby.model.CustomGameFinishedEvent;
import com.blindtest.lobby.model.Lobby;
import com.blindtest.lobby.model.Lobby.LobbyStatus;
import com.blindtest.lobby.model.LobbyParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LobbyService {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameEngineService gameEngineService;
    private final com.blindtest.track.service.PlaylistImportService playlistImportService;

    private final Map<String, Lobby> lobbies = new ConcurrentHashMap<>();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public Lobby createLobby(UUID hostId, String hostName, String avatarUrl, Integer elo,
                             UUID themeId, String themeName, Integer roundsCount) {
        String code = generateUniqueCode();
        int rounds = (roundsCount != null && roundsCount >= 3 && roundsCount <= 30) ? roundsCount : 10;
        int hostElo = elo != null ? elo : 1000;

        LobbyParticipant host = LobbyParticipant.builder()
                .userId(hostId)
                .displayName(hostName != null ? hostName : "Hôte")
                .avatarUrl(avatarUrl)
                .elo(hostElo)
                .isHost(true)
                .isReady(true)
                .build();

        Lobby lobby = Lobby.builder()
                .code(code)
                .hostId(hostId)
                .hostName(host.getDisplayName())
                .themeId(themeId)
                .themeName(themeName != null ? themeName : "Tous thèmes")
                .roundsCount(rounds)
                .status(LobbyStatus.WAITING)
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();

        lobby.getParticipants().put(hostId, host);
        lobbies.put(code, lobby);

        log.info("Lobby créé avec succès : code='{}', hôte='{}' ({})", code, host.getDisplayName(), hostId);
        broadcastLobbyUpdate(lobby);
        return lobby;
    }

    public Lobby joinLobby(String rawCode, UUID userId, String displayName, String avatarUrl, Integer elo) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Code de salon invalide.");
        }
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) {
            throw new IllegalArgumentException("Salon introuvable avec le code: " + code);
        }

        lobby.setLastActivityAt(Instant.now());
        int playerElo = elo != null ? elo : 1000;

        LobbyParticipant existing = lobby.getParticipants().get(userId);
        if (existing != null) {
            existing.setDisplayName(displayName != null ? displayName : existing.getDisplayName());
            existing.setAvatarUrl(avatarUrl != null ? avatarUrl : existing.getAvatarUrl());
            existing.setElo(playerElo);
        } else {
            boolean isFirst = lobby.getParticipants().isEmpty();
            long blueCount = lobby.getParticipants().values().stream().filter(p -> "BLUE".equalsIgnoreCase(p.getTeam())).count();
            long redCount = lobby.getParticipants().values().stream().filter(p -> "RED".equalsIgnoreCase(p.getTeam())).count();
            String assignedTeam = (blueCount <= redCount) ? "BLUE" : "RED";

            LobbyParticipant participant = LobbyParticipant.builder()
                    .userId(userId)
                    .displayName(displayName != null ? displayName : "Joueur")
                    .avatarUrl(avatarUrl)
                    .elo(playerElo)
                    .team(assignedTeam)
                    .isHost(isFirst || userId.equals(lobby.getHostId()))
                    .isReady(true)
                    .build();
            lobby.getParticipants().put(userId, participant);
            if (isFirst) {
                lobby.setHostId(userId);
                lobby.setHostName(participant.getDisplayName());
            }
        }

        log.info("Joueur '{}' ({}) a rejoint le lobby '{}'", displayName, userId, code);
        broadcastLobbyUpdate(lobby);
        return lobby;
    }

    public Lobby leaveLobby(String rawCode, UUID userId) {
        if (rawCode == null) return null;
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) return null;

        lobby.setLastActivityAt(Instant.now());
        lobby.getParticipants().remove(userId);

        if (lobby.getParticipants().isEmpty()) {
            lobbies.remove(code);
            log.info("Lobby '{}' vide et supprimé.", code);
            return null;
        }

        // Si l'hôte est parti, transférer le statut d'hôte au premier joueur restant
        if (userId.equals(lobby.getHostId())) {
            LobbyParticipant newHost = lobby.getParticipants().values().iterator().next();
            newHost.setHost(true);
            lobby.setHostId(newHost.getUserId());
            lobby.setHostName(newHost.getDisplayName());
            log.info("Nouvel hôte pour le lobby '{}' : '{}'", code, newHost.getDisplayName());
        }

        broadcastLobbyUpdate(lobby);
        return lobby;
    }

    public Lobby getLobby(String rawCode) {
        if (rawCode == null) return null;
        return lobbies.get(rawCode.trim().toUpperCase());
    }

    public Lobby updateSettings(String rawCode, UUID requestingUserId, UUID themeId, String themeName, Integer roundsCount) {
        return updateSettings(rawCode, requestingUserId, themeId, themeName, roundsCount, null, null);
    }

    public Lobby updateSettings(String rawCode, UUID requestingUserId, UUID themeId, String themeName, Integer roundsCount, String gameMode, String teamMode) {
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) {
            throw new IllegalArgumentException("Salon introuvable.");
        }
        if (!requestingUserId.equals(lobby.getHostId())) {
            throw new IllegalStateException("Seul l'hôte peut modifier les paramètres du salon.");
        }

        lobby.setLastActivityAt(Instant.now());
        if (themeName != null) {
            lobby.setThemeId(themeId);
            lobby.setThemeName(themeName);
            if (!themeName.startsWith("🎧")) {
                lobby.setCustomPlaylistUrl(null);
                lobby.setCustomPlaylistName(null);
                lobby.setCustomPlaylistProvider(null);
                lobby.getCustomPlaylistTracks().clear();
            }
        }
        if (roundsCount != null && roundsCount >= 3 && roundsCount <= 30) {
            lobby.setRoundsCount(roundsCount);
        }
        if (gameMode != null && !gameMode.isBlank()) {
            lobby.setGameMode("NO_BUZZER".equalsIgnoreCase(gameMode) ? "NO_BUZZER" : "BUZZER");
        }
        if (teamMode != null && !teamMode.isBlank()) {
            lobby.setTeamMode("TEAMS".equalsIgnoreCase(teamMode) ? "TEAMS" : "INDIVIDUAL");
        }

        broadcastLobbyUpdate(lobby);
        return lobby;
    }

    public Lobby setCustomPlaylist(String rawCode, UUID userId, String playlistUrl) {
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) {
            throw new IllegalArgumentException("Salon introuvable.");
        }
        if (!userId.equals(lobby.getHostId())) {
            throw new IllegalStateException("Seul l'hôte peut configurer une playlist personnalisée.");
        }

        com.blindtest.track.service.PlaylistImportService.CustomPlaylistResult result =
                playlistImportService.importPlaylist(playlistUrl);

        if (result.tracks().isEmpty()) {
            throw new IllegalArgumentException("Aucun morceau avec extrait 30s disponible dans cette playlist.");
        }

        lobby.setCustomPlaylistUrl(playlistUrl);
        lobby.setCustomPlaylistName(result.title());
        lobby.setCustomPlaylistProvider(result.provider());
        lobby.setCustomPlaylistTracks(new ArrayList<>(result.tracks()));
        lobby.setThemeId(null);
        lobby.setThemeName("🎧 " + result.title());
        lobby.setLastActivityAt(Instant.now());

        broadcastLobbyUpdate(lobby);
        log.info("Playlist personnalisée ({}) configurée pour le salon {} : '{}' ({} morceaux disponibles).",
                result.provider(), code, result.title(), result.tracks().size());
        return lobby;
    }

    public Lobby clearCustomPlaylist(String rawCode, UUID userId) {
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) {
            throw new IllegalArgumentException("Salon introuvable.");
        }
        if (!userId.equals(lobby.getHostId())) {
            throw new IllegalStateException("Seul l'hôte peut réinitialiser la playlist.");
        }

        lobby.setCustomPlaylistUrl(null);
        lobby.setCustomPlaylistName(null);
        lobby.setCustomPlaylistProvider(null);
        lobby.getCustomPlaylistTracks().clear();
        lobby.setThemeId(null);
        lobby.setThemeName("Tous thèmes");
        lobby.setLastActivityAt(Instant.now());

        broadcastLobbyUpdate(lobby);
        log.info("Playlist personnalisée réinitialisée pour le salon {}.", code);
        return lobby;
    }

    public Lobby switchTeam(String rawCode, UUID userId, String targetTeam) {
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) {
            throw new IllegalArgumentException("Salon introuvable.");
        }
        LobbyParticipant participant = lobby.getParticipants().get(userId);
        if (participant == null) {
            throw new IllegalArgumentException("Participant introuvable.");
        }
        String normalizedTeam = "RED".equalsIgnoreCase(targetTeam) ? "RED" : "BLUE";
        participant.setTeam(normalizedTeam);
        lobby.setLastActivityAt(Instant.now());
        broadcastLobbyUpdate(lobby);
        log.info("Joueur '{}' a rejoint l'équipe {} dans le salon {}", participant.getDisplayName(), normalizedTeam, code);
        return lobby;
    }

    public Lobby startGame(String rawCode, UUID requestingUserId) {
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) {
            throw new IllegalArgumentException("Salon introuvable.");
        }
        if (!requestingUserId.equals(lobby.getHostId())) {
            throw new IllegalStateException("Seul l'hôte peut lancer la partie.");
        }
        if (lobby.getParticipants().isEmpty()) {
            throw new IllegalStateException("Impossible de lancer un salon sans joueur.");
        }

        lobby.setLastActivityAt(Instant.now());
        lobby.setStatus(LobbyStatus.PLAYING);

        GameSession session = gameEngineService.createCustomMatch(
                code,
                lobby.getParticipantsList(),
                lobby.getThemeId(),
                lobby.getRoundsCount(),
                lobby.getGameMode(),
                lobby.getTeamMode(),
                lobby.getCustomPlaylistTracks()
        );

        lobby.setActiveGameId(session.getGameId());

        Map<String, Object> gameStartPayload = Map.of(
                "event", "LOBBY_GAME_START",
                "lobbyCode", code,
                "gameId", session.getGameId().toString(),
                "themeId", lobby.getThemeId() != null ? lobby.getThemeId().toString() : "",
                "themeName", lobby.getThemeName(),
                "roundsCount", lobby.getRoundsCount(),
                "gameMode", lobby.getGameMode() != null ? lobby.getGameMode() : "BUZZER",
                "teamMode", lobby.getTeamMode() != null ? lobby.getTeamMode() : "INDIVIDUAL",
                "customPlaylistName", lobby.getCustomPlaylistName() != null ? lobby.getCustomPlaylistName() : "",
                "participantsCount", lobby.getParticipants().size()
        );

        messagingTemplate.convertAndSend("/topic/lobby/" + code, gameStartPayload);
        broadcastLobbyUpdate(lobby);
        log.info("Partie personnalisée démarrée pour le lobby '{}' [gameId={}] mode={} teamMode={} avec {} joueurs.",
                code, session.getGameId(), lobby.getGameMode(), lobby.getTeamMode(), lobby.getParticipants().size());

        return lobby;
    }

    public Lobby returnToLobby(String rawCode, UUID requestingUserId) {
        String code = rawCode.trim().toUpperCase();
        Lobby lobby = lobbies.get(code);
        if (lobby == null) return null;

        lobby.setLastActivityAt(Instant.now());
        lobby.setStatus(LobbyStatus.WAITING);

        Map<String, Object> returnPayload = Map.of(
                "event", "LOBBY_RETURN",
                "lobbyCode", code
        );
        messagingTemplate.convertAndSend("/topic/lobby/" + code, returnPayload);
        broadcastLobbyUpdate(lobby);
        return lobby;
    }

    @EventListener
    public void onCustomGameFinished(CustomGameFinishedEvent event) {
        if (event == null || event.getLobbyCode() == null) return;
        Lobby lobby = lobbies.get(event.getLobbyCode().toUpperCase());
        if (lobby == null) return;

        lobby.setLastActivityAt(Instant.now());
        lobby.setStatus(LobbyStatus.FINISHED);
        lobby.setLastGameLeaderboard(event.getLeaderboard());

        // Mettre à jour les scores de fin de manche de chaque participant pour affichage dans le salon
        if (event.getFinalScores() != null) {
            event.getFinalScores().forEach((userId, score) -> {
                LobbyParticipant p = lobby.getParticipants().get(userId);
                if (p != null) {
                    p.setLastGameScore(score);
                }
            });
        }

        broadcastLobbyUpdate(lobby);
        log.info("Partie personnalisée terminée pour le lobby '{}'. Scores mis à jour.", lobby.getCode());
    }

    public void broadcastLobbyUpdate(Lobby lobby) {
        if (lobby == null) return;
        messagingTemplate.convertAndSend("/topic/lobby/" + lobby.getCode(), Map.of(
                "event", "LOBBY_UPDATED",
                "lobby", lobby
        ));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!lobbies.containsKey(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupInactiveLobbies() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(4));
        lobbies.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().getLastActivityAt().isBefore(cutoff);
            if (expired) {
                log.info("Lobby inactif expiré et nettoyé : {}", entry.getKey());
            }
            return expired;
        });
    }
}
