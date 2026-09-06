package com.blindtest.matchmaking;

import org.springframework.stereotype.Component;

@Component
public class EloCalculator {

    public static final int DEFAULT_INITIAL_ELO = 1000;
    public static final int MINIMUM_ELO = 100;

    public record EloResult(
            int newEloPlayer1,
            int newEloPlayer2,
            int eloChangePlayer1,
            int eloChangePlayer2
    ) {}

    /**
     * Calcule la nouvelle cote ELO des deux joueurs à l'issue d'un match.
     *
     * @param elo1         ELO actuel du joueur 1
     * @param elo2         ELO actuel du joueur 2
     * @param score1       Score final (points) du joueur 1
     * @param score2       Score final (points) du joueur 2
     * @param gamesPlayed1 Nombre total de matchs déjà joués par le joueur 1
     * @param gamesPlayed2 Nombre total de matchs déjà joués par le joueur 2
     * @return EloResult avec les nouveaux scores et les variations (deltas)
     */
    public EloResult calculate(int elo1, int elo2, int score1, int score2, int gamesPlayed1, int gamesPlayed2) {
        double expected1 = calculateExpectedScore(elo1, elo2);
        double expected2 = 1.0 - expected1;

        double actual1;
        double actual2;
        if (score1 > score2) {
            actual1 = 1.0;
            actual2 = 0.0;
        } else if (score1 < score2) {
            actual1 = 0.0;
            actual2 = 1.0;
        } else {
            actual1 = 0.5;
            actual2 = 0.5;
        }

        int k1 = resolveKFactor(gamesPlayed1, elo1);
        int k2 = resolveKFactor(gamesPlayed2, elo2);

        int change1 = (int) Math.round(k1 * (actual1 - expected1));
        int change2 = (int) Math.round(k2 * (actual2 - expected2));

        int newElo1 = Math.max(MINIMUM_ELO, elo1 + change1);
        int newElo2 = Math.max(MINIMUM_ELO, elo2 + change2);

        return new EloResult(newElo1, newElo2, change1, change2);
    }

    public static double calculateExpectedScore(int playerElo, int opponentElo) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentElo - playerElo) / 400.0));
    }

    public static int resolveKFactor(int gamesPlayed, int currentElo) {
        if (gamesPlayed < 20) {
            return 40; // Phase de calibrage / placement rapide
        } else if (currentElo >= 2200) {
            return 16; // Maîtres / haut de classement (stabilisation)
        } else {
            return 24; // Régime normal
        }
    }
}
