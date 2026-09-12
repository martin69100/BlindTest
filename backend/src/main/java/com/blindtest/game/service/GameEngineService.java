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
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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

    public int getActiveGamesCount() {
        return activeSessions.size();
    }

    public int getActivePlayersCount() {
        return activeSessions.values().stream()
                .mapToInt(s -> s.isSolo() ? 1 : (s.getPlayerIds() != null && !s.getPlayerIds().isEmpty() ? s.getPlayerIds().size() : 2))
                .sum();
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

        if (player1Id != null) {
            session.getPlayerIds().add(player1Id);
            session.getPlayerNames().put(player1Id, p1Name != null ? p1Name : "Joueur 1");
            session.getPlayerScores().put(player1Id, 0);
        }
        if (player2Id != null) {
            session.getPlayerIds().add(player2Id);
            session.getPlayerNames().put(player2Id, p2Name != null ? p2Name : "Joueur 2");
            session.getPlayerScores().put(player2Id, 0);
        }

        activeSessions.put(gameId, session);
        log.info("Nouvelle session créée [gameId={}] P1: '{}', P2: '{}' (Solo={}).", gameId, p1Name, p2Name, session.isSolo());

        // Pré-rafraîchissement asynchrone des 10 morceaux Deezer en arrière-plan
        CompletableFuture.runAsync(() -> {
            for (Track t : tracks) {
                try {
                    deezerClientService.getFreshPreviewUrl(t);
                    Thread.sleep(50);
                } catch (Exception e) {
                    log.warn("Pré-rafraîchissement ignoré pour track {}: {}", t.getId(), e.getMessage());
                }
            }
        });

        return session;
    }

    /**
     * Initialise une nouvelle partie personnalisée (Multi-joueurs Lobby, sans impact ELO).
     */
    /**
     * Initialise une nouvelle partie personnalisée (Multi-joueurs Lobby, sans impact ELO).
     */
    public GameSession createCustomMatch(String lobbyCode,
                                         Collection<com.blindtest.lobby.model.LobbyParticipant> participants,
                                         UUID themeId,
                                         int roundsCount) {
        return createCustomMatch(lobbyCode, participants, themeId, roundsCount, "BUZZER", "INDIVIDUAL");
    }

    public GameSession createCustomMatch(String lobbyCode,
                                         Collection<com.blindtest.lobby.model.LobbyParticipant> participants,
                                         UUID themeId,
                                         int roundsCount,
                                         String gameMode,
                                         String teamMode) {
        int rounds = (roundsCount >= 3 && roundsCount <= 30) ? roundsCount : roundsPerMatch;
        List<Track> tracks = selectTracksForGame(themeId, rounds);

        UUID gameId = UUID.randomUUID();
        List<com.blindtest.lobby.model.LobbyParticipant> partsList = new ArrayList<>(participants);

        UUID p1Id = !partsList.isEmpty() ? partsList.get(0).getUserId() : null;
        String p1Name = !partsList.isEmpty() ? partsList.get(0).getDisplayName() : "Joueur 1";
        UUID p2Id = partsList.size() > 1 ? partsList.get(1).getUserId() : null;
        String p2Name = partsList.size() > 1 ? partsList.get(1).getDisplayName() : null;

        String effectiveGameMode = (gameMode != null && !gameMode.isBlank()) ? gameMode : "BUZZER";
        String effectiveTeamMode = (teamMode != null && !teamMode.isBlank()) ? teamMode : "INDIVIDUAL";

        GameSession session = GameSession.builder()
                .gameId(gameId)
                .isCustom(true)
                .lobbyCode(lobbyCode)
                .gameMode(effectiveGameMode)
                .teamMode(effectiveTeamMode)
                .player1Id(p1Id)
                .player2Id(p2Id)
                .player1Name(p1Name)
                .player2Name(p2Name)
                .themeId(themeId)
                .playlist(tracks)
                .currentRoundIndex(0)
                .currentRoundId(UUID.randomUUID())
                .state(SessionState.WAITING_READY)
                .build();

        session.getTeamScores().put("BLUE", 0);
        session.getTeamScores().put("RED", 0);

        for (com.blindtest.lobby.model.LobbyParticipant p : partsList) {
            if (p.getUserId() != null) {
                session.getPlayerIds().add(p.getUserId());
                session.getPlayerNames().put(p.getUserId(), p.getDisplayName() != null ? p.getDisplayName() : "Joueur");
                session.getPlayerAvatars().put(p.getUserId(), p.getAvatarUrl() != null ? p.getAvatarUrl() : "");
                session.getPlayerElos().put(p.getUserId(), p.getElo());
                session.getPlayerScores().put(p.getUserId(), 0);
                String team = p.getTeam() != null ? p.getTeam() : "BLUE";
                session.getPlayerTeams().put(p.getUserId(), team);
            }
        }

        activeSessions.put(gameId, session);
        log.info("Session personnalisée créée [gameId={}, lobby={}] mode={}, teamMode={} avec {} joueurs.",
                gameId, lobbyCode, effectiveGameMode, effectiveTeamMode, session.getPlayerIds().size());

        // Pré-rafraîchissement asynchrone des morceaux Deezer
        CompletableFuture.runAsync(() -> {
            for (Track t : tracks) {
                try {
                    deezerClientService.getFreshPreviewUrl(t);
                    Thread.sleep(50);
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
        if (freshPreviewUrl == null || freshPreviewUrl.isBlank()) {
            log.warn("Aucun flux audio disponible pour trackId={} ({}-{}). Passage à la suivante.",
                    track.getId(), track.getArtist(), track.getTitle());
            if (session.hasMoreRounds()) {
                session.nextRound();
                startCurrentRound(gameId);
            } else {
                finishMatch(gameId);
            }
            return;
        }

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
                entry("isCustom", session.isCustom()),
                entry("lobbyCode", session.getLobbyCode() != null ? session.getLobbyCode() : ""),
                entry("gameMode", session.getGameMode() != null ? session.getGameMode() : "BUZZER"),
                entry("teamMode", session.getTeamMode() != null ? session.getTeamMode() : "INDIVIDUAL"),
                entry("teamScores", session.getTeamScores()),
                entry("playerTeams", session.getPlayerTeams()),
                entry("player1Id", session.getPlayer1Id() != null ? session.getPlayer1Id().toString() : ""),
                entry("player2Id", session.getPlayer2Id() != null ? session.getPlayer2Id().toString() : ""),
                entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                entry("playerScores", session.getPlayerScores()),
                entry("leaderboard", buildLeaderboard(session)),
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
            int totalRequired = session.getTotalPlayers();
            if (session.isSolo() || session.getReadyPlayers().size() >= totalRequired) {
                session.cancelScheduledTask();
                startCurrentRound(gameId);
            } else {
                int waitSec = session.isCustom() ? 8 : 12;
                session.setScheduledTask(scheduler.schedule(() -> {
                    GameSession s = getSession(gameId);
                    if (s != null && s.getState() == SessionState.WAITING_READY) {
                        log.info("Délai d'attente écoulé pour gameId={}. Démarrage automatique.", gameId);
                        startCurrentRound(gameId);
                    }
                }, waitSec, TimeUnit.SECONDS));
            }
        } else if (session.getState() == SessionState.PLAYING) {
            // Joueur reconnecté ou en retard : resynchroniser sans perturber le joueur actif
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
                entry("event", "ROUND_RESYNC"),
                entry("targetPlayerId", playerId != null ? playerId.toString() : ""),
                entry("gameId", gameId),
                entry("gameMode", session.getGameMode() != null ? session.getGameMode() : "BUZZER"),
                entry("teamMode", session.getTeamMode() != null ? session.getTeamMode() : "INDIVIDUAL"),
                entry("teamScores", session.getTeamScores()),
                entry("playerTeams", session.getPlayerTeams()),
                entry("player1Id", session.getPlayer1Id() != null ? session.getPlayer1Id().toString() : ""),
                entry("player2Id", session.getPlayer2Id() != null ? session.getPlayer2Id().toString() : ""),
                entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                entry("roundId", session.getCurrentRoundId()),
                entry("roundNumber", session.getCurrentRoundIndex() + 1),
                entry("totalRounds", session.getPlaylist().size()),
                entry("previewUrl", freshPreviewUrl != null ? freshPreviewUrl : ""),
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
        if ("NO_BUZZER".equalsIgnoreCase(session.getGameMode())) {
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

            String buzzerName = session.getPlayerNames().getOrDefault(playerId,
                    playerId.equals(session.getPlayer1Id()) ? session.getPlayer1Name() : session.getPlayer2Name());
            if (buzzerName == null || buzzerName.isBlank()) buzzerName = "Joueur";

            Map<String, Object> buzzPayload = Map.of(
                    "event", "PLAYER_BUZZED",
                    "buzzerPlayerId", playerId,
                    "buzzerName", buzzerName,
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
                session.setTitleFoundByPlayerId(playerId);
            } else {
                session.setArtistFound(true);
                session.setArtistFoundByPlayerId(playerId);
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
                    handleFailedAttempt(gameId, s, playerId, "Bonus expiré");
                }
            }, bonusDurationSeconds, TimeUnit.SECONDS));

            Map<String, Object> correctPayload = Map.of(
                    "event", "FIRST_ANSWER_CORRECT",
                    "playerId", playerId,
                    "foundType", result.guessType().name(),
                    "foundName", result.matchedName(),
                    "bonusDurationSeconds", bonusDurationSeconds,
                    "currentScores", Map.of("player1", session.getPlayer1Score(), "player2", session.getPlayer2Score()),
                    "playerScores", session.getPlayerScores(),
                    "leaderboard", buildLeaderboard(session)
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, correctPayload);

        } else {
            // Échec ou timeout de saisie du 1er essai -> La main passe si possible
            log.info("Saisie échouée pour joueur={} gameId={}. Application du vol de main.", playerId, gameId);
            handleFailedAttempt(gameId, session, playerId, rawGuess);
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
                session.setTitleFoundByPlayerId(playerId);
            } else {
                session.setArtistFound(true);
                session.setArtistFoundByPlayerId(playerId);
            }
            updateThemeStats(playerId, session.getThemeId(), false, true);
            log.info("Joueur {} a réussi le bonus (+1 pt) pour le morceau '{}' ! Titre et Artiste validés.", playerId, track.getTitle());
            endRound(gameId);
        } else {
            // Échec bonus : le joueur a trouvé l'un mais pas l'autre -> La main passe à l'autre joueur
            log.info("Joueur {} a échoué au bonus pour gameId={}. La main passe.", playerId);
            handleFailedAttempt(gameId, session, playerId, rawGuess);
        }
    }

    /**
     * Gestion centralisée d'une tentative manquée (1ère saisie ratée ou bonus raté).
     * En ranked : si un joueur trouve rien ou l'un des deux éléments mais pas le second,
     * la main passe à l'autre pendant le temps audio restant, et le premier joueur ne peut plus tenter.
     * Si les deux ont tenté sans trouver les deux, la manche se termine.
     */
    private void handleFailedAttempt(UUID gameId, GameSession session, UUID playerId, String rawGuess) {
        session.cancelScheduledTask();
        session.getPlayersBuzzedInRound().add(playerId);

        String playerName = session.getPlayerNames().getOrDefault(playerId,
                playerId.equals(session.getPlayer1Id()) ? session.getPlayer1Name() : session.getPlayer2Name());
        if (playerName == null || playerName.isBlank()) playerName = "Joueur";
        String guessText = (rawGuess != null && !rawGuess.isBlank()) ? rawGuess.trim() : "Temps écoulé";

        // Enregistrer la tentative ratée dans la manche
        session.getWrongGuessesInRound().add(new GameSession.WrongGuessEntry(playerId, playerName, guessText));

        // Diffuser immédiatement l'événement de mauvaise réponse pour que tout le monde la voie
        Map<String, Object> wrongPayload = Map.of(
                "event", "ANSWER_WRONG",
                "playerId", playerId.toString(),
                "playerName", playerName,
                "guess", guessText
        );
        messagingTemplate.convertAndSend("/topic/game/" + gameId, wrongPayload);

        int totalPlayers = session.getTotalPlayers();
        boolean canOpponentSteal = totalPlayers > 1
                && session.getPlayersBuzzedInRound().size() < totalPlayers
                && session.getRoundRemainingDurationMs() > 1000;

        if (canOpponentSteal) {
            // Relance de la manche pour les autres joueurs avec le temps audio restant
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

            Map<String, Object> stealPayload = Map.ofEntries(
                    entry("event", "ANSWER_FAILED_STEAL_OPEN"),
                    entry("failedPlayerId", playerId),
                    entry("failedPlayerName", playerName),
                    entry("wrongGuess", guessText),
                    entry("remainingAudioMs", session.getRoundRemainingDurationMs()),
                    entry("titleFound", session.isTitleFound()),
                    entry("artistFound", session.isArtistFound()),
                    entry("firstFoundType", session.getFirstFoundType() != null ? session.getFirstFoundType().name() : ""),
                    entry("currentScores", Map.of("player1", session.getPlayer1Score(), "player2", session.getPlayer2Score())),
                    entry("playerScores", session.getPlayerScores()),
                    entry("leaderboard", buildLeaderboard(session))
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, stealPayload);
        } else {
            // Tous les joueurs ont tenté ou plus de temps -> fin de manche
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
                handleFailedAttempt(gameId, session, playerId, "Langue au chat");
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

        String titleFoundByName = session.getTitleFoundByPlayerId() != null
                ? session.getPlayerNames().getOrDefault(session.getTitleFoundByPlayerId(), "")
                : "";
        String artistFoundByName = session.getArtistFoundByPlayerId() != null
                ? session.getPlayerNames().getOrDefault(session.getArtistFoundByPlayerId(), "")
                : "";

        Map<String, Integer> scoresStringMap = new HashMap<>();
        session.getPlayerScores().forEach((k, v) -> scoresStringMap.put(k.toString(), v));

        // Enregistrer la manche dans l'historique complet de la partie
        GameSession.RoundHistoryEntry roundEntry = GameSession.RoundHistoryEntry.builder()
                .roundNumber(session.getCurrentRoundIndex() + 1)
                .title(track.getTitle())
                .artist(track.getArtist())
                .albumName(track.getAlbumName())
                .albumCoverUrl(track.getAlbumCoverUrl())
                .previewUrl(track.getPreviewUrl())
                .titleFound(session.isTitleFound())
                .artistFound(session.isArtistFound())
                .titleFoundByPlayerId(session.getTitleFoundByPlayerId())
                .artistFoundByPlayerId(session.getArtistFoundByPlayerId())
                .titleFoundByName(titleFoundByName)
                .artistFoundByName(artistFoundByName)
                .player1Score(session.getPlayer1Score())
                .player2Score(session.getPlayer2Score())
                .playerScores(scoresStringMap)
                .teamScores(new HashMap<>(session.getTeamScores()))
                .wrongGuesses(new ArrayList<>(session.getWrongGuessesInRound()))
                .build();
        session.getRoundHistory().add(roundEntry);

        List<Map<String, Object>> leaderboard = buildLeaderboard(session);

        Map<String, Object> revealPayload = Map.ofEntries(
                entry("event", "ROUND_END"),
                entry("isCustom", session.isCustom()),
                entry("lobbyCode", session.getLobbyCode() != null ? session.getLobbyCode() : ""),
                entry("gameMode", session.getGameMode() != null ? session.getGameMode() : "BUZZER"),
                entry("teamMode", session.getTeamMode() != null ? session.getTeamMode() : "INDIVIDUAL"),
                entry("teamScores", session.getTeamScores()),
                entry("playerTeams", session.getPlayerTeams()),
                entry("roundNumber", session.getCurrentRoundIndex() + 1),
                entry("player1Id", session.getPlayer1Id() != null ? session.getPlayer1Id().toString() : ""),
                entry("player2Id", session.getPlayer2Id() != null ? session.getPlayer2Id().toString() : ""),
                entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                entry("track", Map.of(
                        "title", track.getTitle(),
                        "artist", track.getArtist(),
                        "albumName", track.getAlbumName() != null ? track.getAlbumName() : "",
                        "albumCoverUrl", track.getAlbumCoverUrl() != null ? track.getAlbumCoverUrl() : "",
                        "previewUrl", track.getPreviewUrl() != null ? track.getPreviewUrl() : ""
                )),
                entry("scores", Map.of(
                        "player1", session.getPlayer1Score(),
                        "player2", session.getPlayer2Score()
                )),
                entry("playerScores", session.getPlayerScores()),
                entry("leaderboard", leaderboard),
                entry("titleFound", session.isTitleFound()),
                entry("artistFound", session.isArtistFound()),
                entry("titleFoundByPlayerId", session.getTitleFoundByPlayerId() != null ? session.getTitleFoundByPlayerId().toString() : ""),
                entry("artistFoundByPlayerId", session.getArtistFoundByPlayerId() != null ? session.getArtistFoundByPlayerId().toString() : ""),
                entry("titleFoundByName", titleFoundByName),
                entry("artistFoundByName", artistFoundByName),
                entry("wrongGuesses", session.getWrongGuessesInRound()),
                entry("isLastRound", !session.hasMoreRounds()),
                entry("autoAdvanceSeconds", 5)
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
     * Forfait / Abandon de partie (Solo, Versus ou Custom).
     */
    @Transactional
    public void handleForfeit(UUID gameId, UUID forfeitPlayerId) {
        GameSession session = activeSessions.get(gameId);
        if (session == null) return;

        if (session.isCustom()) {
            log.info("Joueur {} a quitté la partie personnalisée {}.", forfeitPlayerId, gameId);
            session.getPlayerIds().remove(forfeitPlayerId);
            session.getPlayerScores().remove(forfeitPlayerId);
            List<Map<String, Object>> leaderboard = buildLeaderboard(session);
            Map<String, Object> forfeitPayload = Map.of(
                    "event", "PLAYER_FORFEIT_CUSTOM",
                    "forfeitPlayerId", forfeitPlayerId.toString(),
                    "leaderboard", leaderboard,
                    "scores", session.getPlayerScores()
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, forfeitPayload);
            if (session.getPlayerIds().isEmpty()) {
                activeSessions.remove(gameId);
                session.cancelScheduledTask();
                session.setState(SessionState.FINISHED);
            }
            return;
        }

        activeSessions.remove(gameId);
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
     * Clôture définitive du match à l'issue de toutes les manches.
     */
    @Transactional
    public void finishMatch(UUID gameId) {
        GameSession session = activeSessions.remove(gameId);
        if (session == null) return;

        session.cancelScheduledTask();
        session.setState(SessionState.FINISHED);

        // Partie Personnalisée : ELO inchangé !
        if (session.isCustom()) {
            List<Map<String, Object>> leaderboard = buildLeaderboard(session);
            Map<String, Object> customFinishedPayload = Map.ofEntries(
                    entry("event", "MATCH_FINISHED"),
                    entry("isCustom", true),
                    entry("lobbyCode", session.getLobbyCode() != null ? session.getLobbyCode() : ""),
                    entry("gameMode", session.getGameMode() != null ? session.getGameMode() : "BUZZER"),
                    entry("teamMode", session.getTeamMode() != null ? session.getTeamMode() : "INDIVIDUAL"),
                    entry("teamScores", session.getTeamScores()),
                    entry("playerTeams", session.getPlayerTeams()),
                    entry("player1Id", session.getPlayer1Id() != null ? session.getPlayer1Id().toString() : ""),
                    entry("player2Id", session.getPlayer2Id() != null ? session.getPlayer2Id().toString() : ""),
                    entry("player1Name", session.getPlayer1Name() != null ? session.getPlayer1Name() : ""),
                    entry("player2Name", session.getPlayer2Name() != null ? session.getPlayer2Name() : ""),
                    entry("player1Score", session.getPlayer1Score()),
                    entry("player2Score", session.getPlayer2Score()),
                    entry("player1EloChange", 0),
                    entry("player2EloChange", 0),
                    entry("scores", session.getPlayerScores()),
                    entry("playerScores", session.getPlayerScores()),
                    entry("leaderboard", leaderboard),
                    entry("roundHistory", session.getRoundHistory())
            );

            messagingTemplate.convertAndSend("/topic/game/" + gameId, customFinishedPayload);

            if (session.getLobbyCode() != null) {
                eventPublisher.publishEvent(new com.blindtest.lobby.model.CustomGameFinishedEvent(
                        session.getLobbyCode(),
                        gameId,
                        session.getPlayerScores(),
                        leaderboard
                ));
            }
            log.info("Partie personnalisée terminée [gameId={}, lobby={}]", gameId, session.getLobbyCode());
            return;
        }

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
                        entry("player2NewElo", eloRes.newEloPlayer2()),
                        entry("roundHistory", session.getRoundHistory())
                );

                messagingTemplate.convertAndSend("/topic/game/" + gameId, matchFinishedPayload);
                return;
            }
        }

        Map<String, Object> soloFinishedPayload = Map.of(
                "event", "SOLO_MATCH_FINISHED",
                "player1Score", session.getPlayer1Score(),
                "finalScore", session.getPlayer1Score(),
                "roundHistory", session.getRoundHistory()
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

    public List<Map<String, Object>> buildLeaderboard(GameSession session) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (session == null || session.getPlayerIds() == null) return list;
        for (UUID pid : session.getPlayerIds()) {
            Map<String, Object> map = new HashMap<>();
            map.put("playerId", pid.toString());
            map.put("playerName", session.getPlayerNames().getOrDefault(pid, "Joueur"));
            map.put("avatarUrl", session.getPlayerAvatars().getOrDefault(pid, ""));
            map.put("elo", session.getPlayerElos().getOrDefault(pid, 1000));
            map.put("score", session.getPlayerScores().getOrDefault(pid, 0));
            map.put("team", session.getPlayerTeams().getOrDefault(pid, "BLUE"));
            list.add(map);
        }
        list.sort((a, b) -> Integer.compare((int) b.get("score"), (int) a.get("score")));
        return list;
    }

    /**
     * Traitement d'une réponse en mode NO_BUZZER (tous les joueurs peuvent proposer des réponses simultanément pendant l'écoute).
     */
    public void handleFreeAnswer(UUID gameId, UUID playerId, String rawGuess) {
        GameSession session = getSession(gameId);
        if (session == null || session.getState() != SessionState.PLAYING) return;
        if (!"NO_BUZZER".equalsIgnoreCase(session.getGameMode())) return;
        if (rawGuess == null || rawGuess.isBlank()) return;

        Track track = session.getCurrentTrack();
        if (track == null) return;

        boolean myTitleFound = session.getRoundFoundTitles().contains(playerId);
        boolean myArtistFound = session.getRoundFoundArtists().contains(playerId);

        // Si le joueur a déjà validé à la fois le Titre et l'Artiste sur cette manche, il ne peut plus marquer
        if (myTitleFound && myArtistFound) return;

        VerificationResult result = levenshteinMatcher.evaluate(rawGuess, track, myTitleFound, myArtistFound);

        if (result.matched()) {
            String foundType = result.guessType() == GuessType.TITLE ? "TITLE" : "ARTIST";
            String playerName = session.getPlayerNames().getOrDefault(playerId, "Joueur");

            if (result.guessType() == GuessType.TITLE && !myTitleFound) {
                session.getRoundFoundTitles().add(playerId);
                session.addScore(playerId, 1);
                updateThemeStats(playerId, session.getThemeId(), true, false);
                log.info("[NO_BUZZER] Joueur '{}' a trouvé le TITRE '{}' !", playerName, result.matchedName());
            } else if (result.guessType() == GuessType.ARTIST && !myArtistFound) {
                session.getRoundFoundArtists().add(playerId);
                session.addScore(playerId, 1);
                updateThemeStats(playerId, session.getThemeId(), false, true);
                log.info("[NO_BUZZER] Joueur '{}' a trouvé l'ARTISTE '{}' !", playerName, result.matchedName());
            } else {
                return;
            }

            Map<String, Object> correctPayload = Map.of(
                    "event", "FREE_ANSWER_CORRECT",
                    "playerId", playerId.toString(),
                    "playerName", playerName,
                    "foundType", foundType,
                    "foundName", result.matchedName(),
                    "playerTeam", session.getPlayerTeams().getOrDefault(playerId, "BLUE"),
                    "playerScores", session.getPlayerScores(),
                    "teamScores", session.getTeamScores(),
                    "leaderboard", buildLeaderboard(session)
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, correctPayload);

            // Si tous les joueurs ont trouvé le titre et l'artiste, clore la manche en avance
            int total = session.getTotalPlayers();
            if (total > 0 && session.getRoundFoundTitles().size() >= total && session.getRoundFoundArtists().size() >= total) {
                log.info("[NO_BUZZER] Tous les joueurs ont tout trouvé ! Fin anticipée de la manche.");
                endRound(gameId);
            }
        } else {
            // Mauvaise réponse en mode libre : feedback pour le joueur sans interruption audio
            String playerName = session.getPlayerNames().getOrDefault(playerId, "Joueur");
            Map<String, Object> wrongPayload = Map.of(
                    "event", "FREE_ANSWER_WRONG",
                    "playerId", playerId.toString(),
                    "playerName", playerName,
                    "guess", rawGuess.trim()
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, wrongPayload);
        }
    }
}
