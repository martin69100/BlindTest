package com.blindtest.game.controller;

import com.blindtest.game.model.GameSession;
import com.blindtest.game.service.GameEngineService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameWsController {

    private final GameEngineService gameEngineService;

    @Data
    public static class GameActionDto {
        private UUID playerId;
        private String guess;
    }

    /**
     * Confirmation que le joueur est prêt dans l'arène.
     */
    @MessageMapping("/game/{gameId}/ready")
    public void onPlayerReady(@DestinationVariable UUID gameId, @Payload GameActionDto dto) {
        log.info("Joueur {} prêt pour la partie {}", dto.getPlayerId(), gameId);
        gameEngineService.handlePlayerReady(gameId, dto.getPlayerId());
    }

    /**
     * Traitement du buzz (Espace ou bouton).
     */
    @MessageMapping("/game/{gameId}/buzz")
    public void onPlayerBuzz(@DestinationVariable UUID gameId, @Payload GameActionDto dto) {
        gameEngineService.handleBuzz(gameId, dto.getPlayerId());
    }

    /**
     * Soumission de la première proposition (Titre OU Artiste).
     */
    @MessageMapping("/game/{gameId}/answer")
    public void onPlayerAnswer(@DestinationVariable UUID gameId, @Payload GameActionDto dto) {
        GameSession session = gameEngineService.getSession(gameId);
        if (session != null && "NO_BUZZER".equalsIgnoreCase(session.getGameMode())) {
            gameEngineService.handleFreeAnswer(gameId, dto.getPlayerId(), dto.getGuess());
        } else {
            gameEngineService.handleAnswer(gameId, dto.getPlayerId(), dto.getGuess());
        }
    }

    /**
     * Soumission de la deuxième proposition (Bonus 10s).
     */
    @MessageMapping("/game/{gameId}/bonus")
    public void onPlayerBonusAnswer(@DestinationVariable UUID gameId, @Payload GameActionDto dto) {
        gameEngineService.handleBonusAnswer(gameId, dto.getPlayerId(), dto.getGuess());
    }

    /**
     * Passage à la manche suivante après l'écran de révélation.
     * Autorisé uniquement en solo : en multijoueur, le délai de transition est imposé à tous les joueurs.
     */
    @MessageMapping("/game/{gameId}/next-round")
    public void onNextRound(@DestinationVariable UUID gameId) {
        GameSession session = gameEngineService.getSession(gameId);
        if (session != null && session.getState() == GameSession.SessionState.ROUND_REVEAL) {
            if (!session.isSolo()) {
                log.info("Passage manuel de transition ignoré en multijoueur pour gameId={}", gameId);
                return;
            }
            session.cancelScheduledTask();
            if (session.hasMoreRounds()) {
                session.nextRound();
                gameEngineService.startCurrentRound(gameId);
            } else {
                gameEngineService.finishMatch(gameId);
            }
        }
    }

    /**
     * Abandon / Forfeit de partie.
     */
    @MessageMapping("/game/{gameId}/forfeit")
    public void onPlayerForfeit(@DestinationVariable UUID gameId, @Payload GameActionDto dto) {
        log.info("Demande d'abandon reçue pour gameId={} par le joueur {}", gameId, dto.getPlayerId());
        gameEngineService.handleForfeit(gameId, dto.getPlayerId());
    }

    /**
     * Passer la manche (Skip / Révélation immédiate avec 0 point).
     */
    @MessageMapping("/game/{gameId}/skip")
    public void onPlayerSkip(@DestinationVariable UUID gameId, @Payload GameActionDto dto) {
        log.info("Demande de skip de manche reçue pour gameId={} par le joueur {}", gameId, dto.getPlayerId());
        gameEngineService.handleSkipRound(gameId, dto.getPlayerId());
    }
}
