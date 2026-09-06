package com.blindtest.matchmaking.service;

import com.blindtest.game.model.GameSession;
import com.blindtest.game.service.GameEngineService;
import com.blindtest.matchmaking.model.MatchmakingTicket;
import com.blindtest.user.entity.User;
import com.blindtest.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final GameEngineService gameEngineService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${blindtest.matchmaking.base-range:50}")
    private int baseRange;

    @Value("${blindtest.matchmaking.range-step:25}")
    private int rangeStep;

    @Value("${blindtest.matchmaking.step-interval-seconds:3}")
    private int stepIntervalSeconds;

    @Value("${blindtest.matchmaking.max-range:300}")
    private int maxRange;

    // File d'attente thread-safe
    private final List<MatchmakingTicket> queue = new CopyOnWriteArrayList<>();

    public synchronized boolean joinQueue(UUID userId, UUID preferredThemeId) {
        // Éviter les doublons
        queue.removeIf(ticket -> ticket.userId().equals(userId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        MatchmakingTicket ticket = new MatchmakingTicket(
                user.getId(),
                user.getDisplayName(),
                user.getElo(),
                preferredThemeId,
                System.currentTimeMillis()
        );

        queue.add(ticket);
        log.info("Joueur '{}' [ELO={}] a rejoint la file de matchmaking (Thème={}).", user.getDisplayName(), user.getElo(), preferredThemeId);
        return true;
    }

    public synchronized boolean leaveQueue(UUID userId) {
        return queue.removeIf(ticket -> ticket.userId().equals(userId));
    }

    public boolean isInQueue(UUID userId) {
        return queue.stream().anyMatch(t -> t.userId().equals(userId));
    }

    /**
     * Scheduler périodique d'appariement (s'exécute chaque seconde).
     */
    @Scheduled(fixedRateString = "${blindtest.matchmaking.scheduler-period-ms:1000}")
    public synchronized void processMatchmaking() {
        if (queue.size() < 2) {
            return;
        }

        // Trier par ancienneté dans la file (les plus anciens d'abord)
        List<MatchmakingTicket> sortedQueue = new ArrayList<>(queue);
        sortedQueue.sort(Comparator.comparingLong(MatchmakingTicket::joinedAtEpochMs));

        Set<UUID> matchedInCycle = new HashSet<>();

        for (int i = 0; i < sortedQueue.size(); i++) {
            MatchmakingTicket t1 = sortedQueue.get(i);
            if (matchedInCycle.contains(t1.userId())) continue;

            MatchmakingTicket bestMatch = null;
            int smallestEloDiff = Integer.MAX_VALUE;

            for (int j = i + 1; j < sortedQueue.size(); j++) {
                MatchmakingTicket t2 = sortedQueue.get(j);
                if (matchedInCycle.contains(t2.userId())) continue;

                // Vérification de compatibilité de thème
                boolean themeCompatible = (t1.preferredThemeId() == null || t2.preferredThemeId() == null
                        || t1.preferredThemeId().equals(t2.preferredThemeId()));

                if (!themeCompatible) continue;

                int eloDiff = Math.abs(t1.elo() - t2.elo());
                int range1 = t1.getCurrentRange(baseRange, rangeStep, stepIntervalSeconds, maxRange);
                int range2 = t2.getCurrentRange(baseRange, rangeStep, stepIntervalSeconds, maxRange);
                int allowableRange = Math.max(range1, range2);

                if (eloDiff <= allowableRange && eloDiff < smallestEloDiff) {
                    smallestEloDiff = eloDiff;
                    bestMatch = t2;
                }
            }

            if (bestMatch != null) {
                matchedInCycle.add(t1.userId());
                matchedInCycle.add(bestMatch.userId());

                queue.remove(t1);
                queue.remove(bestMatch);

                UUID chosenTheme = (t1.preferredThemeId() != null) ? t1.preferredThemeId() : bestMatch.preferredThemeId();
                GameSession session = gameEngineService.createVersusMatch(
                        t1.userId(), t1.displayName(),
                        bestMatch.userId(), bestMatch.displayName(),
                        chosenTheme
                );

                log.info("MATCH TROUVÉ ! '{}' (ELO {}) vs '{}' (ELO {}) [diff={} | gameId={}]",
                        t1.displayName(), t1.elo(), bestMatch.displayName(), bestMatch.elo(), smallestEloDiff, session.getGameId());

                // Notifier les deux joueurs via WebSocket avec les identifiants complets
                Map<String, Object> matchNotification = Map.of(
                        "event", "MATCH_FOUND",
                        "gameId", session.getGameId(),
                        "player1Id", t1.userId().toString(),
                        "player2Id", bestMatch.userId().toString(),
                        "player1Name", t1.displayName(),
                        "player2Name", bestMatch.displayName(),
                        "player1Elo", t1.elo(),
                        "player2Elo", bestMatch.elo()
                );

                messagingTemplate.convertAndSend("/topic/matchmaking/" + t1.userId(), matchNotification);
                messagingTemplate.convertAndSend("/topic/matchmaking/" + bestMatch.userId(), matchNotification);
            }
        }
    }
}
