package com.blindtest.fuzzymatch;

import com.blindtest.game.fuzzymatch.StringNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringNormalizerTest {

    @ParameterizedTest(name = "Normalisation de ''{0}'' attendu -> ''{1}''")
    @CsvSource(value = {
            "Céline Dion,celine dion",
            "P!nk,p nk",
            "AC/DC,ac dc",
            "The Beatles,beatles",
            "Les Rita Mitsouko,rita mitsouko",
            "Billie Jean (Remastered 2003),billie jean",
            "Stan (feat. Dido),stan",
            "Around the World [Radio Edit],around the world",
            "L'Aventurier,aventurier",
            "  Michael    Jackson  ,michael jackson"
    })
    void testNormalizeCases(String input, String expected) {
        String result = StringNormalizer.normalize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Chaîne vide ou nulle renvoie une chaîne vide")
    void testEmptyAndNull() {
        assertEquals("", StringNormalizer.normalize(null));
        assertEquals("", StringNormalizer.normalize("   "));
    }
}
