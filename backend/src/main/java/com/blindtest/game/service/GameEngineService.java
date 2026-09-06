package com.blindtest.game.service;

import com.blindtest.game.entity.Match;
import com.blindtest.game.entity.MatchRound.GuessType;
import com.blindtest.game.fuzzymatch.LevenshteinMatcher;
import com.blindtest.game.fuzzymatch.VerificationResult;
import com.blindtest.game.model.GameSession;
import com.blindtest.game.model.GameSession.SessionState;
import com.blindtest.game.repository.MatchRepository;
import com.blindtest.matchmaking.EloCalculator;
import com.blindtest.theme.entity.UserThemeStats;
import com.blindtest.theme.repository.UserThemeStatsRepository;
import com.blindtest.track.entity.Track;
import com.blindtest.track.repository.TrackRepository;
import com.blindtest.user.entity.User;
import com.blindtest.user.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEngineService {

    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final UserThemeStatsRepository statsRepository;
    private final LevenshteinMatcher levenshteinMatcher;
    private final EloCalculator eloCalculator;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.blindtest.track.service.DeezerClientService deezerClientService;

    @Value("${blindtest.game.rounds-per-match:10}")
    private int roundsPerMatch;

    @Value("${blindtest.game.round-duration-seconds:20}")
    private int roundDurationSeconds;

    @Value("${blindtest.game.first-guess-timeout-seconds:5}")
    private int firstGuessTimeoutSeconds;

    @Value("${blindtest.game.bonus-duration-seconds:10}")
    private int bonusDurationSeconds;

    // Scheduler pour les timeouts automatiques (fin des 20s, 5s de saisie, 10s de bonus, 5s de révélation)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Registre mémoire des parties actives
    private final Map<UUID, GameSession> activeSessions = new ConcurrentHashMap<>();

    @PreDestroy
    public void cleanup() {
        scheduler.shutdownNow();
    }

    public GameSession getSession(UUID gameId) {
        return activeSessions.get(gameId);
    }

    /**
     * Initialise une nouvelle partie en mode Versus (Classé) ou Solo.
     */
    public GameSession createVersusMatch(UUID player1Id, String p1Name, UUID player2Id, String p2Name, UUID themeId) {
        List<Track> tracks = selectTracksForGame(themeId, roundsPerMatch);

        UUID gameId = UUID.randomUUID();
        GameSession session = GameSession.builder()
                .gameId(gameId)
                .player1Id(player1Id)
                .player2Id(player2Id)
                .player1Name(p1Name)
                .player2Name(p2Name)
                .themeId(themeId)
                .playlist(tracks)
                .currentRoundIndex(0)
                .currentRoundId(UUID.randomUUID())
                .state(SessionState.WAITING_READY)
                .build();

        activeSessions.put(gameId, session);
        log.info("Nouvelle session créée [gameId={}] P1: '{}', P2: '{}' (Solo={}).", gameId, p1Name, p2Name, session.isSolo());

        // Pré-rafraîchissement asynchrone des 10 morceaux Deezer en arrière-plan
        CompletableFuture.runAsync(() -> {
            for (Track t : tracks) {
                try {
                    deezerClientService.getFreshPreviewUrl(t);
                } catch (Exception e) {
                    log.warn("Pré-rafraîchissement ignoré pour track {}: {}", t.getId(), e.getMessage());
                }
            }
        });

        return session;
    }

    /**
     * Démarre la manche courante et arme le timer automatique de 20 secondes.
     */
    public void startCurrentRound(UUID gameId) {
        GameSession session = getSession(gameId);
        if (session == null) return;

        session.cancelScheduledTask();

        Track track = session.getCurrentTrack();
        if (track == null) {
            finishMatch(gameId);
            return;
        }

        // Obtention du flux audio Deezer avec token CDN garanti valide
        String freshPreviewUrl = deezerClientService.getFreshPreviewUrl(track);

        session.setState(SessionState.PLAYING);
        session.setRoundStartTimestamp(System.currentTimeMillis());
        session.setRoundRemainingDurationMs(roundDurationSeconds * 1000L);
        session.getBuzzLock().set(false);
        session.setCurrentBuzzerPlayerId(null);
        session.getPlayersBuzzedInRound().clear();

        UUID roundId = session.getCurrentRoundId();

        // Armer le timeout serveur des 20 secondes (passe automatiquement au morceau suivant si personne ne buzze)
        session.setScheduledTask(scheduler.schedule(() -> {
            GameSession s = getSession(gameId);
            if (s != null && s.getState() == SessionState.PLAYING && roundId.equals(s.getCurrentRoundId())) {
                log.info("Fin du temps d'écoute (20s) sans buzz pour gameId={}. Fin de manche automatique.", gameId);
                endRound(gameId);
            }
        }, roundDurationSeconds, TimeUnit.SECONDS));

        // Anti-triche strict : aucune métadonnée textuelle n'est envoyée
        Map<String, Object> roundStartPayload = Map.of(
                "event", "ROUND_START",
                "gameId", gameId,
                "roundId", session.getCurrentRoundId(),
                "roundNumber", session.getCurrentRoundIndex() + 1,
                "totalRounds", session.getPlaylist().size(),
                "previewUrl", freshPreviewUrl,
                "durationSeconds", roundDurationSeconds,
                "serverTimestamp", System.currentTimeMillis()
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId, roundStartPayload);
    }

    /**
     * Traitement atomique du Buzz d'un joueur et armement du décompte de 5 secondes.
     */
    public boolean handleBuzz(UUID gameId, UUID playerId) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() != SessionState.PLAYING) {
            return false;
        }

        // Si le joueur a déjà buzzé et échoué lors de cette manche (Option A), il ne peut plus buzzer
        if (session.getPlayersBuzzedInRound().contains(playerId)) {
            return false;
        }

        // Exclusion mutuelle atomique : le premier arrivé bloque instantanément l'autre
        if (session.getBuzzLock().compareAndSet(false, true)) {
            session.cancelScheduledTask(); // Annuler le timer d'écoute des 20s
            session.setState(SessionState.BUZZED);
            session.setCurrentBuzzerPlayerId(playerId);
            session.setBuzzTimestamp(System.currentTimeMillis());

            // Calcul du temps restant pour une reprise éventuelle
            long elapsed = session.getBuzzTimestamp() - session.getRoundStartTimestamp();
            long remaining = Math.max(0, session.getRoundRemainingDurationMs() - elapsed);
            session.setRoundRemainingDurationMs(remaining);

            // Armer le décompte automatique de saisie (5 secondes max)
            session.setScheduledTask(scheduler.schedule(() -> {
                GameSession s = getSession(gameId);
                if (s != null && s.getState() == SessionState.BUZZED && playerId.equals(s.getCurrentBuzzerPlayerId())) {
                    log.info("Expiration du délai de saisie (5s) pour joueur={}. Traitement échec.", playerId);
                    handleAnswer(gameId, playerId, ""); // Réponse vide = échec
                }
            }, firstGuessTimeoutSeconds, TimeUnit.SECONDS));

            Map<String, Object> buzzPayload = Map.of(
                    "event", "PLAYER_BUZZED",
                    "buzzerPlayerId", playerId,
                    "buzzerName", playerId.equals(session.getPlayer1Id()) ? session.getPlayer1Name() : session.getPlayer2Name(),
                    "inputTimeoutSeconds", firstGuessTimeoutSeconds,
                    "remainingAudioMs", remaining
            );

            messagingTemplate.convertAndSend("/topic/game/" + gameId, buzzPayload);
            return true;
        }

        return false;
    }

    /**
     * Évaluation de la proposition de réponse (Titre ou Artiste).
     */
    public void handleAnswer(UUID gameId, UUID playerId, String rawGuess) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() != SessionState.BUZZED) return;
        if (!playerId.equals(session.getCurrentBuzzerPlayerId())) return;

        session.cancelScheduledTask(); // Annuler le timer de saisie des 5s

        long elapsed = System.currentTimeMillis() - session.getBuzzTimestamp();
        boolean timeout = elapsed > (firstGuessTimeoutSeconds * 1000L + 800); // grâce réseau

        Track track = session.getCurrentTrack();
        VerificationResult result = (!timeout && rawGuess != null && !rawGuess.isBlank()) ?
                levenshteinMatcher.evaluate(rawGuess, track, session.isTitleFound(), session.isArtistFound())
                : VerificationResult.notMatched();

        if (result.matched()) {
            // Première réponse validée (+1 point)
            session.addScore(playerId, 1);
            if (result.guessType() == GuessType.TITLE) {
                session.setTitleFound(true);
            } else {
                session.setArtistFound(true);
            }
            session.setFirstFoundType(result.guessType());
            session.setState(SessionState.BONUS_WINDOW);

            // Mettre à jour les stats par thème en base
            updateThemeStats(playerId, session.getThemeId(), true, false);

            // Armer le timer automatique de 10 secondes pour le bonus
            session.setScheduledTask(scheduler.schedule(() -> {
                GameSession s = getSession(gameId);
                if (s != null && s.getState() == SessionState.BONUS_WINDOW) {
                    log.info("Expiration du bonus (10s) pour gameId={}. Clôture de manche.", gameId);
                    endRound(gameId);
                }
            }, bonusDurationSeconds, TimeUnit.SECONDS));

            Map<String, Object> correctPayload = Map.of(
                    "event", "FIRST_ANSWER_CORRECT",
                    "playerId", playerId,
                    "foundType", result.guessType().name(),
                    "foundName", result.matchedName(),
                    "bonusDurationSeconds", bonusDurationSeconds,
                    "currentScores", Map.of("player1", session.getPlayer1Score(), "player2", session.getPlayer2Score())
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, correctPayload);

        } else {
            // Échec ou timeout de saisie -> Application de l'Option A (Vol de main)
            session.getPlayersBuzzedInRound().add(playerId);
            boolean canOpponentSteal = !session.isSolo()
                    && session.getPlayersBuzzedInRound().size() < 2
                    && session.getRoundRemainingDurationMs() > 1000;

            if (canOpponentSteal) {
                // Relance de la manche pour l'adversaire
                session.setState(SessionState.PLAYING);
                session.setRoundStartTimestamp(System.currentTimeMillis());
                session.setCurrentBuzzerPlayerId(null);
                session.getBuzzLock().set(false);

                // Armer le timer pour le temps restant
                long remainingSec = Math.max(1, session.getRoundRemainingDurationMs() / 1000);
                UUID roundId = session.getCurrentRoundId();
                session.setScheduledTask(scheduler.schedule(() -> {
                    GameSession s = getSession(gameId);
                    if (s != null && s.getState() == SessionState.PLAYING && roundId.equals(s.getCurrentRoundId())) {
                        log.info("Temps restant écoulé après vol pour gameId={}. Fin de manche.", gameId);
                        endRound(gameId);
                    }
                }, remainingSec, TimeUnit.SECONDS));

                Map<String, Object> stealPayload = Map.of(
                        "event", "ANSWER_FAILED_STEAL_OPEN",
                        "failedPlayerId", playerId,
                        "remainingAudioMs", session.getRoundRemainingDurationMs()
                );
                messagingTemplate.convertAndSend("/topic/game/" + gameId, stealPayload);

            } else {
                // Les deux ont échoué ou plus de temps -> fin de manche
                endRound(gameId);
            }
        }
    }

    /**
     * Évaluation de la tentative de bonus (2ème information dans les 10 secondes).
     */
    public void handleBonusAnswer(UUID gameId, UUID playerId, String rawGuess) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() != SessionState.BONUS_WINDOW) return;
        if (!playerId.equals(session.getCurrentBuzzerPlayerId())) return;

        session.cancelScheduledTask(); // Annuler le timer bonus

        Track track = session.getCurrentTrack();
        VerificationResult result = levenshteinMatcher.evaluate(
                rawGuess,
                track,
                session.isTitleFound(),
                session.isArtistFound()
        );

        if (result.matched()) {
            session.addScore(playerId, 1); // +1 point bonus
            updateThemeStats(playerId, session.getThemeId(), false, true);
            log.info("Joueur {} a réussi le bonus (+1 pt) pour le morceau '{}' !", playerId, track.getTitle());
        }

        endRound(gameId);
    }

    /**
     * Passer la manche courante (Skip / Donner sa langue au chat).
     * Compte comme une tentative manquée (0 point), interrompt l'écoute et affiche immédiatement la révélation.
     */
    public void handleSkipRound(UUID gameId, UUID playerId) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() == SessionState.ROUND_REVEAL || session.getState() == SessionState.FINISHED) {
            return;
        }

        log.info("Joueur {} passe la manche {} pour gameId={}.", playerId, session.getCurrentRoundIndex() + 1, gameId);
        session.cancelScheduledTask();
        endRound(gameId);
    }

    /**
     * Termine la manche en cours, diffuse les métadonnées officielles révélées,
     * et enchaîne automatiquement au morceau suivant après 5 secondes si aucun clic manuel.
     */
    public void endRound(UUID gameId) {
        GameSession session = getSession(gameId);
        if (session == null) return;

        session.cancelScheduledTask();
        session.setState(SessionState.ROUND_REVEAL);
        Track track = session.getCurrentTrack();

        Map<String, Object> revealPayload = Map.of(
                "event", "ROUND_END",
                "roundNumber", session.getCurrentRoundIndex() + 1,
                "track", Map.of(
                        "title", track.getTitle(),
                        "artist", track.getArtist(),
                        "albumName", track.getAlbumName() != null ? track.getAlbumName() : "",
                        "albumCoverUrl", track.getAlbumCoverUrl() != null ? track.getAlbumCoverUrl() : ""
                ),
                "scores", Map.of(
                        "player1", session.getPlayer1Score(),
                        "player2", session.getPlayer2Score()
                ),
                "isLastRound", !session.hasMoreRounds(),
                "autoAdvanceSeconds", 5
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId, revealPayload);

        // Auto-enchaînement après 5 secondes vers la manche suivante ou la fin de partie
        session.setScheduledTask(scheduler.schedule(() -> {
            GameSession s = getSession(gameId);
            if (s != null && s.getState() == SessionState.ROUND_REVEAL) {
                if (s.hasMoreRounds()) {
                    s.nextRound();
                    startCurrentRound(gameId);
                } else {
                    finishMatch(gameId);
                }
            }
        }, 5, TimeUnit.SECONDS));
    }

    /**
     * Forfait / Abandon de partie (Solo ou Versus).
     */
    @Transactional
    public void handleForfeit(UUID gameId, UUID forfeitPlayerId) {
        GameSession session = activeSessions.remove(gameId);
        if (session == null) return;

        session.cancelScheduledTask();
        session.setState(SessionState.FINISHED);

        if (session.isSolo()) {
            log.info("Session solo arrêtée par le joueur {}.", forfeitPlayerId);
            Map<String, Object> soloFinishedPayload = Map.of(
                    "event", "SOLO_MATCH_FINISHED",
                    "player1Score", session.getPlayer1Score(),
                    "finalScore", session.getPlayer1Score(),
                    "forfeit", true
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, soloFinishedPayload);
            return;
        }

        // Mode Versus : l'autre joueur est déclaré vainqueur immédiat !
        UUID winnerId = forfeitPlayerId.equals(session.getPlayer1Id()) ? session.getPlayer2Id() : session.getPlayer1Id();
        User p1 = userRepository.findById(session.getPlayer1Id()).orElse(null);
        User p2 = userRepository.findById(session.getPlayer2Id()).orElse(null);

        if (p1 != null && p2 != null) {
            long p1Games = matchRepository.countCompletedMatchesByUserId(p1.getId());
            long p2Games = matchRepository.countCompletedMatchesByUserId(p2.getId());

            // Score attribué : vainqueur = 10, joueur ayant abandonné = score actuel ou 0
            int p1Score = forfeitPlayerId.equals(p1.getId()) ? 0 : Math.max(session.getPlayer1Score(), 10);
            int p2Score = forfeitPlayerId.equals(p2.getId()) ? 0 : Math.max(session.getPlayer2Score(), 10);

            EloCalculator.EloResult eloRes = eloCalculator.calculate(
                    p1.getElo(), p2.getElo(),
                    p1Score, p2Score,
                    (int) p1Games, (int) p2Games
            );

            p1.setElo(eloRes.newEloPlayer1());
            p2.setElo(eloRes.newEloPlayer2());
            userRepository.save(p1);
            userRepository.save(p2);

            User winner = winnerId.equals(p1.getId()) ? p1 : p2;

            Match match = Match.builder()
                    .player1(p1)
                    .player2(p2)
                    .winner(winner)
                    .player1Score(p1Score)
                    .player2Score(p2Score)
                    .player1EloBefore(p1.getElo() - eloRes.eloChangePlayer1())
                    .player2EloBefore(p2.getElo() - eloRes.eloChangePlayer2())
                    .player1EloChange(eloRes.eloChangePlayer1())
                    .player2EloChange(eloRes.eloChangePlayer2())
                    .status(Match.MatchStatus.ABANDONED)
                    .endedAt(Instant.now())
                    .build();

            matchRepository.save(match);

            Map<String, Object> matchFinishedPayload = Map.of(
                    "event", "MATCH_FINISHED",
                    "forfeit", true,
                    "forfeitPlayerId", forfeitPlayerId,
                    "winnerId", winner.getId().toString(),
                    "player1Score", p1Score,
                    "player2Score", p2Score,
                    "player1EloChange", eloRes.eloChangePlayer1(),
                    "player2EloChange", eloRes.eloChangePlayer2(),
                    "player1NewElo", eloRes.newEloPlayer1(),
                    "player2NewElo", eloRes.newEloPlayer2()
            );

            messagingTemplate.convertAndSend("/topic/game/" + gameId, matchFinishedPayload);
            log.info("Partie versus {} abandonnée par {}. Vainqueur : {}.", gameId, forfeitPlayerId, winner.getDisplayName());
        }
    }

    /**
     * Clôture définitive du match à l'issue des 10 manches.
     */
    @Transactional
    public void finishMatch(UUID gameId) {
        GameSession session = activeSessions.remove(gameId);
        if (session == null) return;

        session.cancelScheduledTask();
        session.setState(SessionState.FINISHED);

        if (!session.isSolo()) {
            User p1 = userRepository.findById(session.getPlayer1Id()).orElse(null);
            User p2 = userRepository.findById(session.getPlayer2Id()).orElse(null);

            if (p1 != null && p2 != null) {
                long p1Games = matchRepository.countCompletedMatchesByUserId(p1.getId());
                long p2Games = matchRepository.countCompletedMatchesByUserId(p2.getId());

                EloCalculator.EloResult eloRes = eloCalculator.calculate(
                        p1.getElo(), p2.getElo(),
                        session.getPlayer1Score(), session.getPlayer2Score(),
                        (int) p1Games, (int) p2Games
                );

                p1.setElo(eloRes.newEloPlayer1());
                p2.setElo(eloRes.newEloPlayer2());
                userRepository.save(p1);
                userRepository.save(p2);

                User winner = null;
                if (session.getPlayer1Score() > session.getPlayer2Score()) winner = p1;
                else if (session.getPlayer2Score() > session.getPlayer1Score()) winner = p2;

                Match match = Match.builder()
                        .player1(p1)
                        .player2(p2)
                        .winner(winner)
                        .player1Score(session.getPlayer1Score())
                        .player2Score(session.getPlayer2Score())
                        .player1EloBefore(p1.getElo() - eloRes.eloChangePlayer1())
                        .player2EloBefore(p2.getElo() - eloRes.eloChangePlayer2())
                        .player1EloChange(eloRes.eloChangePlayer1())
                        .player2EloChange(eloRes.eloChangePlayer2())
                        .status(Match.MatchStatus.COMPLETED)
                        .endedAt(Instant.now())
                        .build();

                matchRepository.save(match);

                Map<String, Object> matchFinishedPayload = Map.of(
                        "event", "MATCH_FINISHED",
                        "player1Score", session.getPlayer1Score(),
                        "player2Score", session.getPlayer2Score(),
                        "winnerId", winner != null ? winner.getId().toString() : "DRAW",
                        "player1EloChange", eloRes.eloChangePlayer1(),
                        "player2EloChange", eloRes.eloChangePlayer2(),
                        "player1NewElo", eloRes.newEloPlayer1(),
                        "player2NewElo", eloRes.newEloPlayer2()
                );

                messagingTemplate.convertAndSend("/topic/game/" + gameId, matchFinishedPayload);
                return;
            }
        }

        Map<String, Object> soloFinishedPayload = Map.of(
                "event", "SOLO_MATCH_FINISHED",
                "player1Score", session.getPlayer1Score(),
                "finalScore", session.getPlayer1Score()
        );
        messagingTemplate.convertAndSend("/topic/game/" + gameId, soloFinishedPayload);
    }

    private void updateThemeStats(UUID userId, UUID themeId, boolean correctFirst, boolean correctBonus) {
        if (themeId == null) return;
        statsRepository.findByUserIdAndThemeId(userId, themeId).ifPresent(stats -> {
            if (correctFirst) {
                stats.setVersusCorrectAnswers(stats.getVersusCorrectAnswers() + 1);
                stats.setMasteryPoints(stats.getMasteryPoints() + 5);
            }
            if (correctBonus) {
                stats.setBonusCorrectAnswers(stats.getBonusCorrectAnswers() + 1);
                stats.setMasteryPoints(stats.getMasteryPoints() + 5);
            }
            stats.setUpdatedAt(Instant.now());
            statsRepository.save(stats);
        });
    }

    private List<Track> selectTracksForGame(UUID themeId, int count) {
        List<Track> tracks;
        if (themeId != null) {
            tracks = trackRepository.findRandomTracksByTheme(themeId, count);
        } else {
            tracks = trackRepository.findRandomTracks(count);
        }

        if (tracks.size() < count) {
            List<Track> fallback = trackRepository.findRandomTracks(count);
            return fallback;
        }
        return tracks;
    }
}
