package com.blindtest.matchmaking;

import com.blindtest.game.service.GameEngineService;
import com.blindtest.matchmaking.model.MatchmakingStats;
import com.blindtest.matchmaking.service.MatchmakingService;
import com.blindtest.user.entity.User;
import com.blindtest.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

    @Mock
    private GameEngineService gameEngineService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MatchmakingService matchmakingService;

    @BeforeEach
    void setUp() {
        matchmakingService = new MatchmakingService(gameEngineService, userRepository, messagingTemplate);
    }

    @Test
    @DisplayName("getStats retourne le nombre exact de joueurs en file et en partie")
    void testGetStats() {
        when(gameEngineService.getActivePlayersCount()).thenReturn(4);
        when(gameEngineService.getActiveGamesCount()).thenReturn(2);

        MatchmakingStats stats = matchmakingService.getStats();
        assertEquals(0, stats.inQueue());
        assertEquals(4, stats.inGame());
        assertEquals(2, stats.activeMatches());
    }

    @Test
    @DisplayName("joinQueue et leaveQueue mettent à jour les stats et diffusent via WebSocket")
    void testJoinAndLeaveBroadcast() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .displayName("Alice")
                .elo(1200)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(gameEngineService.getActivePlayersCount()).thenReturn(2);
        when(gameEngineService.getActiveGamesCount()).thenReturn(1);

        boolean joined = matchmakingService.joinQueue(userId, null);
        assertTrue(joined);
        assertTrue(matchmakingService.isInQueue(userId));
        assertEquals(1, matchmakingService.getStats().inQueue());

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/matchmaking/stats"), any(MatchmakingStats.class));

        boolean left = matchmakingService.leaveQueue(userId);
        assertTrue(left);
        assertFalse(matchmakingService.isInQueue(userId));
        assertEquals(0, matchmakingService.getStats().inQueue());

        verify(messagingTemplate, atLeast(2)).convertAndSend(eq("/topic/matchmaking/stats"), any(MatchmakingStats.class));
    }
}
