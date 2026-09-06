package com.blindtest.fuzzymatch;

import com.blindtest.game.entity.MatchRound.GuessType;
import com.blindtest.game.fuzzymatch.LevenshteinMatcher;
import com.blindtest.game.fuzzymatch.StringNormalizer;
import com.blindtest.game.fuzzymatch.VerificationResult;
import com.blindtest.track.entity.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LevenshteinMatcherTest {

    private LevenshteinMatcher matcher;
    private Track sampleTrack;

    @BeforeEach
    void setUp() {
        matcher = new LevenshteinMatcher();
        sampleTrack = Track.builder()
                .title("Billie Jean")
                .normalizedTitle(StringNormalizer.normalize("Billie Jean"))
                .artist("Michael Jackson")
                .normalizedArtist(StringNormalizer.normalize("Michael Jackson"))
                .altTitles(List.of("Billy Jean"))
                .altArtists(List.of("MJ", "King of Pop"))
                .build();
    }

    @Test
    @DisplayName("Deviner exactement le titre")
    void testExactTitleMatch() {
        VerificationResult result = matcher.evaluate("Billie Jean", sampleTrack, false, false);
        assertTrue(result.matched());
        assertEquals(GuessType.TITLE, result.guessType());
        assertEquals("Billie Jean", result.matchedName());
    }

    @Test
    @DisplayName("Deviner le titre avec une faute de frappe (Damerau transposition ou substitution)")
    void testTypoTitleMatch() {
        // "Billei Jean" -> transposition
        VerificationResult result = matcher.evaluate("Billei Jean", sampleTrack, false, false);
        assertTrue(result.matched());
        assertEquals(GuessType.TITLE, result.guessType());
    }

    @Test
    @DisplayName("Deviner l'artiste avec faute de frappe ('Micheal Jackson')")
    void testArtistWithTypo() {
        VerificationResult result = matcher.evaluate("Micheal Jackson", sampleTrack, false, false);
        assertTrue(result.matched());
        assertEquals(GuessType.ARTIST, result.guessType());
        assertEquals("Michael Jackson", result.matchedName());
    }

    @Test
    @DisplayName("Deviner l'artiste via un alias alternatif ('King of Pop')")
    void testArtistAlias() {
        VerificationResult result = matcher.evaluate("King of Pop", sampleTrack, false, false);
        assertTrue(result.matched());
        assertEquals(GuessType.ARTIST, result.guessType());
    }

    @Test
    @DisplayName("Lorsque le titre a déjà été trouvé, seule l'artiste est accepté")
    void testTitleAlreadyFound() {
        // Le joueur re-tape le titre -> refusé
        VerificationResult resultTitleAgain = matcher.evaluate("Billie Jean", sampleTrack, true, false);
        assertFalse(resultTitleAgain.matched());

        // Le joueur tape l'artiste -> accepté
        VerificationResult resultArtist = matcher.evaluate("Michael Jackson", sampleTrack, true, false);
        assertTrue(resultArtist.matched());
        assertEquals(GuessType.ARTIST, resultArtist.guessType());
    }

    @Test
    @DisplayName("Mot court (< 4 caractères) exige une frappe exacte (0 tolérance)")
    void testShortWordsZeroTolerance() {
        Track shortTrack = Track.builder()
                .title("Air")
                .normalizedTitle(StringNormalizer.normalize("Air"))
                .artist("U2")
                .normalizedArtist(StringNormalizer.normalize("U2"))
                .build();

        // "Air" -> exact OK
        assertTrue(matcher.evaluate("Air", shortTrack, false, false).matched());

        // "Aim" ou "Bar" -> refusé (longueur 3, 0 tolérance)
        assertFalse(matcher.evaluate("Aim", shortTrack, false, false).matched());
        assertFalse(matcher.evaluate("Bar", shortTrack, false, false).matched());
    }

    @Test
    @DisplayName("Johnny Hallyday avec fautes ('jhonny', 'johny')")
    void testJohnnyTypo() {
        Track johnny = Track.builder()
                .title("Allumer le feu")
                .normalizedTitle(StringNormalizer.normalize("Allumer le feu"))
                .artist("Johnny Hallyday")
                .normalizedArtist(StringNormalizer.normalize("Johnny Hallyday"))
                .altArtists(List.of("Johnny"))
                .build();

        // Test sur l'alias "Johnny" avec faute "jhonny"
        VerificationResult result = matcher.evaluate("jhonny", johnny, false, false);
        assertTrue(result.matched());
        assertEquals(GuessType.ARTIST, result.guessType());
    }

    @Test
    @DisplayName("Accepter 'muzz', 'muz', 'muze' pour 'Muse' (équivalences phonétiques)")
    void testMuzzMatchesMuse() {
        Track museTrack = Track.builder()
                .title("Uprising")
                .normalizedTitle(StringNormalizer.normalize("Uprising"))
                .artist("Muse")
                .normalizedArtist(StringNormalizer.normalize("Muse"))
                .build();

        assertTrue(matcher.evaluate("muzz", museTrack, false, false).matched(), "muzz doit matcher Muse");
        assertEquals(GuessType.ARTIST, matcher.evaluate("muzz", museTrack, false, false).guessType());

        assertTrue(matcher.evaluate("muz", museTrack, false, false).matched(), "muz doit matcher Muse");
        assertTrue(matcher.evaluate("muze", museTrack, false, false).matched(), "muze doit matcher Muse");

        // Mais des mots distincts ne doivent pas matcher
        assertFalse(matcher.evaluate("rose", museTrack, false, false).matched(), "rose ne doit PAS matcher Muse");
        assertFalse(matcher.evaluate("mule", museTrack, false, false).matched(), "mule ne doit PAS matcher Muse");
    }

    @Test
    @DisplayName("Accepter 'big flo et oli' pour 'Bigflo & Oli' (espacement et conjonctions)")
    void testBigfloEtOliMatchesBigfloAndOli() {
        Track bfoTrack = Track.builder()
                .title("Dommage")
                .normalizedTitle(StringNormalizer.normalize("Dommage"))
                .artist("Bigflo & Oli")
                .normalizedArtist(StringNormalizer.normalize("Bigflo & Oli"))
                .build();

        // Variantes avec espaces et 'et' vs '&'
        assertTrue(matcher.evaluate("big flo et oli", bfoTrack, false, false).matched(), "big flo et oli doit matcher");
        assertEquals(GuessType.ARTIST, matcher.evaluate("big flo et oli", bfoTrack, false, false).guessType());

        assertTrue(matcher.evaluate("bigflo et oli", bfoTrack, false, false).matched(), "bigflo et oli doit matcher");
        assertTrue(matcher.evaluate("big flo & oli", bfoTrack, false, false).matched(), "big flo & oli doit matcher");
        assertTrue(matcher.evaluate("bigflo oli", bfoTrack, false, false).matched(), "bigflo oli doit matcher");
        assertTrue(matcher.evaluate("bigflo", bfoTrack, false, false).matched(), "bigflo seul doit matcher l'artiste");
    }

    @Test
    @DisplayName("Accepter 'ntm' pour 'Suprême NTM' (token significatif)")
    void testNtmMatchesSupremeNtm() {
        Track ntmTrack = Track.builder()
                .title("Seine Saint-Denis Style")
                .normalizedTitle(StringNormalizer.normalize("Seine Saint-Denis Style"))
                .artist("Suprême NTM")
                .normalizedArtist(StringNormalizer.normalize("Suprême NTM"))
                .build();

        VerificationResult resultNtm = matcher.evaluate("ntm", ntmTrack, false, false);
        assertTrue(resultNtm.matched(), "ntm doit matcher Suprême NTM");
        assertEquals(GuessType.ARTIST, resultNtm.guessType());

        assertTrue(matcher.evaluate("supreme", ntmTrack, false, false).matched(), "supreme doit matcher");
        assertTrue(matcher.evaluate("supreme ntm", ntmTrack, false, false).matched(), "supreme ntm doit matcher");
    }

    @Test
    @DisplayName("Acronymes de groupes (SOAD, RATM)")
    void testAcronymMatching() {
        Track soadTrack = Track.builder()
                .title("Chop Suey!")
                .normalizedTitle(StringNormalizer.normalize("Chop Suey!"))
                .artist("System of a Down")
                .normalizedArtist(StringNormalizer.normalize("System of a Down"))
                .build();

        assertTrue(matcher.evaluate("soad", soadTrack, false, false).matched(), "soad doit matcher System of a Down");

        Track ratmTrack = Track.builder()
                .title("Killing in the Name")
                .normalizedTitle(StringNormalizer.normalize("Killing in the Name"))
                .artist("Rage Against the Machine")
                .normalizedArtist(StringNormalizer.normalize("Rage Against the Machine"))
                .build();

        assertTrue(matcher.evaluate("ratm", ratmTrack, false, false).matched(), "ratm doit matcher Rage Against the Machine");
    }

    @Test
    @DisplayName("Sous-phrase de titre ('Teen Spirit' pour 'Smells Like Teen Spirit')")
    void testSubphraseMatching() {
        Track nirvana = Track.builder()
                .title("Smells Like Teen Spirit")
                .normalizedTitle(StringNormalizer.normalize("Smells Like Teen Spirit"))
                .artist("Nirvana")
                .normalizedArtist(StringNormalizer.normalize("Nirvana"))
                .build();

        VerificationResult result = matcher.evaluate("teen spirit", nirvana, false, false);
        assertTrue(result.matched(), "teen spirit doit matcher Smells Like Teen Spirit");
        assertEquals(GuessType.TITLE, result.guessType());
    }

    @Test
    @DisplayName("Prénom courant seul refusé ('David' ne valide pas 'David Bowie', mais 'Bowie' oui)")
    void testCommonFirstNameHandling() {
        Track bowie = Track.builder()
                .title("Space Oddity")
                .normalizedTitle(StringNormalizer.normalize("Space Oddity"))
                .artist("David Bowie")
                .normalizedArtist(StringNormalizer.normalize("David Bowie"))
                .build();

        assertFalse(matcher.evaluate("david", bowie, false, false).matched(), "david seul ne doit pas valider David Bowie");
        assertTrue(matcher.evaluate("bowie", bowie, false, false).matched(), "bowie doit valider David Bowie");
    }

    @Test
    @DisplayName("Accepter l'artiste en feat ('Sia') sur 'David Guetta feat. Sia' ou 'Titanium (feat. Sia)'")
    void testFeaturedArtistMatching() {
        // Cas 1 : Le feat est dans le titre officiel
        Track trackWithFeatInTitle = Track.builder()
                .title("Titanium (feat. Sia)")
                .normalizedTitle(StringNormalizer.normalize("Titanium (feat. Sia)"))
                .artist("David Guetta")
                .normalizedArtist(StringNormalizer.normalize("David Guetta"))
                .build();

        // "Sia" doit être validé comme Artiste
        VerificationResult siaResult1 = matcher.evaluate("Sia", trackWithFeatInTitle, false, false);
        assertTrue(siaResult1.matched(), "Sia doit être validé comme Artiste quand présent dans le titre");
        assertEquals(GuessType.ARTIST, siaResult1.guessType());

        // "David Guetta" et "Guetta" doivent aussi être validés
        assertTrue(matcher.evaluate("David Guetta", trackWithFeatInTitle, false, false).matched());
        assertTrue(matcher.evaluate("Guetta", trackWithFeatInTitle, false, false).matched());

        // "Titanium" doit être validé comme Titre
        VerificationResult titaniumResult1 = matcher.evaluate("Titanium", trackWithFeatInTitle, false, false);
        assertTrue(titaniumResult1.matched());
        assertEquals(GuessType.TITLE, titaniumResult1.guessType());

        // Cas 2 : Le feat est dans le nom d'artiste
        Track trackWithFeatInArtist = Track.builder()
                .title("Titanium")
                .normalizedTitle(StringNormalizer.normalize("Titanium"))
                .artist("David Guetta feat. Sia")
                .normalizedArtist(StringNormalizer.normalize("David Guetta feat. Sia"))
                .build();

        VerificationResult siaResult2 = matcher.evaluate("Sia", trackWithFeatInArtist, false, false);
        assertTrue(siaResult2.matched(), "Sia doit être validé comme Artiste quand présent dans l'artiste");
        assertEquals(GuessType.ARTIST, siaResult2.guessType());
        assertTrue(matcher.evaluate("David Guetta", trackWithFeatInArtist, false, false).matched());
    }
}
