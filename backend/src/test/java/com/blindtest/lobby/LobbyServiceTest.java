package com.blindtest.lobby;

import com.blindtest.game.model.GameSession;
import com.blindtest.game.service.GameEngineService;
import com.blindtest.lobby.model.CustomGameFinishedEvent;
import com.blindtest.lobby.model.Lobby;
import com.blindtest.lobby.service.LobbyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LobbyServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private GameEngineService gameEngineService;

    @InjectMocks
    private LobbyService lobbyService;

    private UUID hostId;
    private UUID player2Id;
    private UUID player3Id;

    @BeforeEach
    void setUp() {
        hostId = UUID.randomUUID();
        player2Id = UUID.randomUUID();
        player3Id = UUID.randomUUID();
    }

    @Test
    @DisplayName("Création d'un lobby avec hôte")
    void testCreateLobby() {
        Lobby lobby = lobbyService.createLobby(
                hostId, "Alice", "https://avatar.com/alice.png", 1350,
                null, "Tous thèmes", 10
        );

        assertNotNull(lobby);
        assertNotNull(lobby.getCode());
        assertEquals(6, lobby.getCode().length());
        assertEquals(hostId, lobby.getHostId());
        assertEquals("Alice", lobby.getHostName());
        assertEquals(1, lobby.getParticipants().size());
        assertTrue(lobby.getParticipants().get(hostId).isHost());
        assertEquals(1350, lobby.getParticipants().get(hostId).getElo());
        assertEquals(Lobby.LobbyStatus.WAITING, lobby.getStatus());
    }

    @Test
    @DisplayName("Rejoindre un lobby existant avec plusieurs joueurs")
    void testJoinLobby() {
        Lobby lobby = lobbyService.createLobby(
                hostId, "Alice", null, 1200, null, "Pop", 10
        );
        String code = lobby.getCode();

        lobbyService.joinLobby(code, player2Id, "Bob", null, 1400);
        lobbyService.joinLobby(code, player3Id, "Charlie", null, 1100);

        Lobby updated = lobbyService.getLobby(code);
        assertEquals(3, updated.getParticipants().size());
        assertEquals(1400, updated.getParticipants().get(player2Id).getElo());
        assertFalse(updated.getParticipants().get(player2Id).isHost());
        assertEquals(1100, updated.getParticipants().get(player3Id).getElo());
    }

    @Test
    @DisplayName("Démarrage de la partie par l'hôte")
    void testStartGame() {
        Lobby lobby = lobbyService.createLobby(
                hostId, "Alice", null, 1200, null, "Rock", 10
        );
        String code = lobby.getCode();
        lobbyService.joinLobby(code, player2Id, "Bob", null, 1400);

        UUID mockGameId = UUID.randomUUID();
        GameSession mockSession = mock(GameSession.class);
        when(mockSession.getGameId()).thenReturn(mockGameId);

        when(gameEngineService.createCustomMatch(eq(code), anyCollection(), any(), anyInt()))
                .thenReturn(mockSession);

        Lobby startedLobby = lobbyService.startGame(code, hostId);
        assertEquals(Lobby.LobbyStatus.PLAYING, startedLobby.getStatus());
        assertEquals(mockGameId, startedLobby.getActiveGameId());
    }

    @Test
    @DisplayName("Non-hôte ne peut pas lancer la partie")
    void testNonHostCannotStartGame() {
        Lobby lobby = lobbyService.createLobby(
                hostId, "Alice", null, 1200, null, "Rock", 10
        );
        String code = lobby.getCode();
        lobbyService.joinLobby(code, player2Id, "Bob", null, 1400);

        assertThrows(IllegalStateException.class, () -> {
            lobbyService.startGame(code, player2Id);
        });
    }

    @Test
    @DisplayName("Retour au salon après une partie")
    void testReturnToLobby() {
        Lobby lobby = lobbyService.createLobby(
                hostId, "Alice", null, 1200, null, "Rock", 10
        );
        String code = lobby.getCode();

        lobbyService.returnToLobby(code, hostId);
        assertEquals(Lobby.LobbyStatus.WAITING, lobbyService.getLobby(code).getStatus());
    }

    @Test
    @DisplayName("Gestion de fin de partie personnalisée et mise à jour des scores")
    void testOnCustomGameFinished() {
        Lobby lobby = lobbyService.createLobby(
                hostId, "Alice", null, 1200, null, "Rock", 10
        );
        String code = lobby.getCode();
        lobbyService.joinLobby(code, player2Id, "Bob", null, 1400);

        Map<UUID, Integer> finalScores = Map.of(hostId, 8, player2Id, 5);
        List<Map<String, Object>> leaderboard = List.of(
                Map.of("playerId", hostId.toString(), "playerName", "Alice", "score", 8),
                Map.of("playerId", player2Id.toString(), "playerName", "Bob", "score", 5)
        );

        lobbyService.onCustomGameFinished(new CustomGameFinishedEvent(code, UUID.randomUUID(), finalScores, leaderboard));

        Lobby finishedLobby = lobbyService.getLobby(code);
        assertEquals(Lobby.LobbyStatus.FINISHED, finishedLobby.getStatus());
        assertEquals(8, finishedLobby.getParticipants().get(hostId).getLastGameScore());
        assertEquals(5, finishedLobby.getParticipants().get(player2Id).getLastGameScore());
    }
}
