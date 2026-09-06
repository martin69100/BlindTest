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
import static java.util.Map.entry;

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

    @Value("${blindtest.game.first-guess-timeout-seconds:8}")
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

        // Anti-triche strict : aucune métadonnée textuelle secrète n'est envoyée
        Map<String, Object> roundStartPayload = Map.ofEntries(
                entry("event", "ROUND_START"),
                entry("gameId", gameId),
                entry("player1Id", session.getPlayer1Id() != null ? session.getPlayer1Id().toString() : ""),
                entry("player2Id", session.getPlayer2Id() != null ? session.getPlayer2Id().toString() : ""),
                entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                entry("roundId", session.getCurrentRoundId()),
                entry("roundNumber", session.getCurrentRoundIndex() + 1),
                entry("totalRounds", session.getPlaylist().size()),
                entry("previewUrl", freshPreviewUrl),
                entry("durationSeconds", roundDurationSeconds),
                entry("serverTimestamp", System.currentTimeMillis())
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId, roundStartPayload);
    }

    /**
     * Gestion de la confirmation 'prêt' d'un joueur dans l'arène.
     */
    public void handlePlayerReady(UUID gameId, UUID playerId) {
        GameSession session = getSession(gameId);
        if (session == null) return;

        if (session.getState() == SessionState.WAITING_READY) {
            session.getReadyPlayers().add(playerId);
            if (session.isSolo() || session.getReadyPlayers().size() >= 2) {
                session.cancelScheduledTask();
                startCurrentRound(gameId);
            } else {
                // Si le premier joueur est prêt en versus, lancer un délai de secours de 3s au cas où le 2e joueur tarde
                session.setScheduledTask(scheduler.schedule(() -> {
                    GameSession s = getSession(gameId);
                    if (s != null && s.getState() == SessionState.WAITING_READY) {
                        log.info("Délai d'attente du 2ème joueur écoulé pour gameId={}. Démarrage automatique de la 1ère manche.", gameId);
                        startCurrentRound(gameId);
                    }
                }, 3, TimeUnit.SECONDS));
            }
        } else if (session.getState() == SessionState.PLAYING) {
            // Joueur reconnecté ou en retard : resynchroniser immédiatement l'état audio et manche
            syncSessionStateForPlayer(gameId, playerId);
        }
    }

    public void syncSessionStateForPlayer(UUID gameId, UUID playerId) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() == SessionState.FINISHED) return;

        Track track = session.getCurrentTrack();
        if (track == null) return;

        String freshPreviewUrl = deezerClientService.getFreshPreviewUrl(track);
        long elapsed = System.currentTimeMillis() - session.getRoundStartTimestamp();
        long remaining = Math.max(0, session.getRoundRemainingDurationMs() - elapsed);

        Map<String, Object> syncPayload = Map.ofEntries(
                entry("event", "ROUND_START"),
                entry("gameId", gameId),
                entry("player1Id", session.getPlayer1Id() != null ? session.getPlayer1Id().toString() : ""),
                entry("player2Id", session.getPlayer2Id() != null ? session.getPlayer2Id().toString() : ""),
                entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                entry("roundId", session.getCurrentRoundId()),
                entry("roundNumber", session.getCurrentRoundIndex() + 1),
                entry("totalRounds", session.getPlaylist().size()),
                entry("previewUrl", freshPreviewUrl),
                entry("durationSeconds", (int) Math.max(1, remaining / 1000)),
                entry("serverTimestamp", System.currentTimeMillis())
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId, syncPayload);
    }

    /**
     * Traitement atomique du Buzz d'un joueur et armement du décompte de saisie.
     */
    public boolean handleBuzz(UUID gameId, UUID playerId) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() != SessionState.PLAYING) {
            return false;
        }

        // Si le joueur a déjà buzzé et terminé sa tentative lors de cette manche, il ne peut plus buzzer
        if (session.getPlayersBuzzedInRound().contains(playerId)) {
            return false;
        }

        // Exclusion mutuelle atomique : le premier arrivé bloque instantanément l'autre
        if (session.getBuzzLock().compareAndSet(false, true)) {
            session.cancelScheduledTask(); // Annuler le timer d'écoute des 20s
            session.setState(SessionState.BUZZED);
            session.setCurrentBuzzerPlayerId(playerId);
            session.setBuzzTimestamp(System.currentTimeMillis());

            // Calcul du temps audio restant pour une reprise éventuelle
            long elapsed = session.getBuzzTimestamp() - session.getRoundStartTimestamp();
            long remaining = Math.max(0, session.getRoundRemainingDurationMs() - elapsed);
            session.setRoundRemainingDurationMs(remaining);

            // Armer le décompte automatique de saisie
            session.setScheduledTask(scheduler.schedule(() -> {
                GameSession s = getSession(gameId);
                if (s != null && s.getState() == SessionState.BUZZED && playerId.equals(s.getCurrentBuzzerPlayerId())) {
                    log.info("Expiration du délai de saisie ({}s) pour joueur={}. Traitement échec.", firstGuessTimeoutSeconds, playerId);
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

        session.cancelScheduledTask(); // Annuler le timer de saisie

        long elapsed = System.currentTimeMillis() - session.getBuzzTimestamp();
        boolean timeout = elapsed > (firstGuessTimeoutSeconds * 1000L + 1200); // grâce réseau

        Track track = session.getCurrentTrack();
        VerificationResult result = (!timeout && rawGuess != null && !rawGuess.isBlank()) ?
                levenshteinMatcher.evaluate(rawGuess, track, session.isTitleFound(), session.isArtistFound())
                : VerificationResult.notMatched();

        if (result.matched()) {
            // Réponse validée (+1 point)
            session.addScore(playerId, 1);
            if (result.guessType() == GuessType.TITLE) {
                session.setTitleFound(true);
            } else {
                session.setArtistFound(true);
            }
            if (session.getFirstFoundType() == GuessType.NONE) {
                session.setFirstFoundType(result.guessType());
            }

            // Mettre à jour les stats par thème en base
            updateThemeStats(playerId, session.getThemeId(), true, false);

            // Si les deux éléments (titre ET artiste) sont désormais validés (ex: adversaire avait trouvé l'autre) :
            if (session.isTitleFound() && session.isArtistFound()) {
                log.info("Titre et Artiste trouvés pour gameId={} ! Clôture de la manche.", gameId);
                endRound(gameId);
                return;
            }

            // Un seul élément trouvé : le joueur entre en fenêtre bonus (10s) pour le 2ème élément
            session.setState(SessionState.BONUS_WINDOW);

            session.setScheduledTask(scheduler.schedule(() -> {
                GameSession s = getSession(gameId);
                if (s != null && s.getState() == SessionState.BONUS_WINDOW) {
                    log.info("Expiration du bonus (10s) pour gameId={}. La main passe si possible.", gameId);
                    handleFailedAttempt(gameId, s, playerId);
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
            // Échec ou timeout de saisie du 1er essai -> La main passe si possible
            log.info("Saisie échouée pour joueur={} gameId={}. Application du vol de main.", playerId, gameId);
            handleFailedAttempt(gameId, session, playerId);
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
            if (result.guessType() == GuessType.TITLE) {
                session.setTitleFound(true);
            } else {
                session.setArtistFound(true);
            }
            updateThemeStats(playerId, session.getThemeId(), false, true);
            log.info("Joueur {} a réussi le bonus (+1 pt) pour le morceau '{}' ! Titre et Artiste validés.", playerId, track.getTitle());
            endRound(gameId);
        } else {
            // Échec bonus : le joueur a trouvé l'un mais pas l'autre -> La main passe à l'autre joueur
            log.info("Joueur {} a échoué au bonus pour gameId={}. La main passe.", playerId);
            handleFailedAttempt(gameId, session, playerId);
        }
    }

    /**
     * Gestion centralisée d'une tentative manquée (1ère saisie ratée ou bonus raté).
     * En ranked : si un joueur trouve rien ou l'un des deux éléments mais pas le second,
     * la main passe à l'autre pendant le temps audio restant, et le premier joueur ne peut plus tenter.
     * Si les deux ont tenté sans trouver les deux, la manche se termine.
     */
    private void handleFailedAttempt(UUID gameId, GameSession session, UUID playerId) {
        session.cancelScheduledTask();
        session.getPlayersBuzzedInRound().add(playerId);

        boolean canOpponentSteal = !session.isSolo()
                && session.getPlayersBuzzedInRound().size() < 2
                && session.getRoundRemainingDurationMs() > 1000;

        if (canOpponentSteal) {
            // Relance de la manche pour l'adversaire avec le temps audio restant
            session.setState(SessionState.PLAYING);
            session.setRoundStartTimestamp(System.currentTimeMillis());
            session.setCurrentBuzzerPlayerId(null);
            session.getBuzzLock().set(false);

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
                    "remainingAudioMs", session.getRoundRemainingDurationMs(),
                    "titleFound", session.isTitleFound(),
                    "artistFound", session.isArtistFound(),
                    "firstFoundType", session.getFirstFoundType() != null ? session.getFirstFoundType().name() : "",
                    "currentScores", Map.of("player1", session.getPlayer1Score(), "player2", session.getPlayer2Score())
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, stealPayload);
        } else {
            // Les deux ont tenté et n'ont pas trouvé les deux, ou plus de temps -> fin de manche
            log.info("Fin de manche pour gameId={} (tentatives épuisées ou temps écoulé).", gameId);
            endRound(gameId);
        }
    }

    /**
     * Passer la manche courante (Skip / Donner sa langue au chat).
     */
    public void handleSkipRound(UUID gameId, UUID playerId) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() == SessionState.ROUND_REVEAL || session.getState() == SessionState.FINISHED) {
            return;
        }

        log.info("Joueur {} passe la manche {} pour gameId={}.", playerId, session.getCurrentRoundIndex() + 1, gameId);

        if (session.isSolo()) {
            session.cancelScheduledTask();
            endRound(gameId);
        } else {
            // En versus : si le joueur avait la main (BUZZED ou BONUS_WINDOW), cela compte comme échec de sa tentative
            if (playerId.equals(session.getCurrentBuzzerPlayerId()) &&
               (session.getState() == SessionState.BUZZED || session.getState() == SessionState.BONUS_WINDOW)) {
                handleFailedAttempt(gameId, session, playerId);
            }
        }
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
                "player1Id", session.getPlayer1Id() != null ? session.getPlayer1Id().toString() : "",
                "player2Id", session.getPlayer2Id() != null ? session.getPlayer2Id().toString() : "",
                "player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : "",
                "player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : "",
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

            Map<String, Object> matchFinishedPayload = Map.ofEntries(
                    entry("event", "MATCH_FINISHED"),
                    entry("forfeit", true),
                    entry("forfeitPlayerId", forfeitPlayerId),
                    entry("player1Id", session.getPlayer1Id().toString()),
                    entry("player2Id", session.getPlayer2Id().toString()),
                    entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                    entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                    entry("winnerId", winner.getId().toString()),
                    entry("player1Score", p1Score),
                    entry("player2Score", p2Score),
                    entry("player1EloChange", eloRes.eloChangePlayer1()),
                    entry("player2EloChange", eloRes.eloChangePlayer2()),
                    entry("player1NewElo", eloRes.newEloPlayer1()),
                    entry("player2NewElo", eloRes.newEloPlayer2())
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

                Map<String, Object> matchFinishedPayload = Map.ofEntries(
                        entry("event", "MATCH_FINISHED"),
                        entry("player1Id", session.getPlayer1Id().toString()),
                        entry("player2Id", session.getPlayer2Id().toString()),
                        entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                        entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                        entry("player1Score", session.getPlayer1Score()),
                        entry("player2Score", session.getPlayer2Score()),
                        entry("winnerId", winner != null ? winner.getId().toString() : "DRAW"),
                        entry("player1EloChange", eloRes.eloChangePlayer1()),
                        entry("player2EloChange", eloRes.eloChangePlayer2()),
                        entry("player1NewElo", eloRes.newEloPlayer1()),
                        entry("player2NewElo", eloRes.newEloPlayer2())
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
