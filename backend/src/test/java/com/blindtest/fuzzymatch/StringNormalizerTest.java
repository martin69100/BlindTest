package com.blindtest.fuzzymatch;

import com.blindtest.game.fuzzymatch.StringNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("Extraction des artistes en featuring et collaborations")
    void testExtractFeaturedArtists() {
        var feat1 = StringNormalizer.extractFeaturedArtists("Titanium (feat. Sia)");
        assertTrue(feat1.contains("Sia"));

        var feat2 = StringNormalizer.extractFeaturedArtists("David Guetta feat. Sia");
        assertTrue(feat2.contains("Sia"));

        var feat3 = StringNormalizer.extractFeaturedArtists("Get Lucky (feat. Pharrell Williams & Nile Rodgers)");
        assertTrue(feat3.contains("Pharrell Williams"));
        assertTrue(feat3.contains("Nile Rodgers"));

        var feat4 = StringNormalizer.extractFeaturedArtists("Major Lazer & DJ Snake feat. MØ");
        assertTrue(feat4.contains("MØ"));
        assertTrue(feat4.contains("Major Lazer"));
        assertTrue(feat4.contains("DJ Snake"));
    }
}
