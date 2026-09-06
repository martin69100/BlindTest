package com.blindtest.matchmaking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EloCalculatorTest {

    private EloCalculator eloCalculator;

    @BeforeEach
    void setUp() {
        eloCalculator = new EloCalculator();
    }

    @Test
    @DisplayName("Victoire entre deux joueurs de même ELO (1000 vs 1000) en phase de placement (K=40)")
    void testEqualEloPlacementWin() {
        // Joueur 1 gagne 10 à 5, tous deux ont joué 5 parties
        EloCalculator.EloResult result = eloCalculator.calculate(1000, 1000, 10, 5, 5, 5);

        // Espérance = 0.5. Delta = 40 * (1.0 - 0.5) = +20 pour J1, -20 pour J2
        assertEquals(20, result.eloChangePlayer1());
        assertEquals(-20, result.eloChangePlayer2());
        assertEquals(1020, result.newEloPlayer1());
        assertEquals(980, result.newEloPlayer2());
    }

    @Test
    @DisplayName("Match nul entre deux joueurs de même ELO donne 0 point de variation")
    void testDrawEqualElo() {
        EloCalculator.EloResult result = eloCalculator.calculate(1000, 1000, 5, 5, 10, 10);
        assertEquals(0, result.eloChangePlayer1());
        assertEquals(0, result.eloChangePlayer2());
        assertEquals(1000, result.newEloPlayer1());
        assertEquals(1000, result.newEloPlayer2());
    }

    @Test
    @DisplayName("Victoire surprise (Underdog 900 bat Favori 1200 en régime normal K=24)")
    void testUnderdogVictory() {
        // J1 (900 ELO) bat J2 (1200 ELO)
        EloCalculator.EloResult result = eloCalculator.calculate(900, 1200, 10, 8, 30, 30);

        // L'underdog doit gagner beaucoup de points (> 18 points)
        assertTrue(result.eloChangePlayer1() > 18);
        assertTrue(result.eloChangePlayer2() < -18);
        // Conservation des points
        assertEquals(result.eloChangePlayer1(), -result.eloChangePlayer2());
    }

    @Test
    @DisplayName("L'ELO ne descend jamais sous le plancher minimal de 100 points")
    void testFloorElo() {
        EloCalculator.EloResult result = eloCalculator.calculate(110, 1500, 0, 10, 10, 30);
        assertTrue(result.newEloPlayer1() >= 100);
    }
}
