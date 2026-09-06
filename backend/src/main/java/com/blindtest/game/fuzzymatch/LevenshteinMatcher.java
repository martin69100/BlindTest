package com.blindtest.game.fuzzymatch;

import com.blindtest.game.entity.MatchRound.GuessType;
import com.blindtest.track.entity.Track;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class LevenshteinMatcher {

    // Mots vides / stop words qui ne peuvent pas déclencher une validation partielle à eux seuls
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "les", "le", "la", "l", "un", "une", "des", "du", "de", "d",
            "et", "and", "or", "ou", "in", "on", "at", "to", "for", "of", "with", "avec",
            "is", "it", "you", "me", "my", "your", "mon", "ma", "ton", "ta", "son", "sa",
            "ses", "mes", "tes", "ce", "cet", "cette", "ces", "je", "tu", "il", "elle",
            "nous", "vous", "ils", "elles", "all", "tout", "tous", "toute", "toutes"
    );

    // Prénoms courants qui seuls ne suffisent pas à valider un artiste complet (sauf s'ils sont uniques ou en alias)
    private static final Set<String> COMMON_FIRST_NAMES = Set.of(
            "jean", "michel", "david", "claude", "pierre", "paul", "george", "john"
    );

    /**
     * Évalue la saisie d'un joueur par rapport à un morceau en tenant compte des éléments déjà trouvés.
     *
     * @param rawGuess                Texte brut saisi par le joueur
     * @param track                   Entité Track contenant les métadonnées officielles
     * @param titleAlreadyDiscovered  Indique si le titre a déjà été validé dans la manche
     * @param artistAlreadyDiscovered Indique si l'artiste a déjà été validé dans la manche
     * @return VerificationResult indiquant le succès, le type (TITLE ou ARTIST) et le score
     */
    public VerificationResult evaluate(
            String rawGuess,
            Track track,
            boolean titleAlreadyDiscovered,
            boolean artistAlreadyDiscovered
    ) {
        String normalizedGuess = StringNormalizer.normalize(rawGuess);
        if (normalizedGuess.isBlank()) {
            return VerificationResult.notMatched();
        }

        // 1. Vérification contre le Titre (si non encore trouvé)
        if (!titleAlreadyDiscovered) {
            String title = (track.getTitle() != null) ? track.getTitle() : track.getNormalizedTitle();
            String normalizedTitle = StringNormalizer.normalize(title);
            MatchCandidate titleMatch = checkCandidate(normalizedGuess, normalizedTitle, track.getAltTitles(), false);
            if (titleMatch.matched()) {
                return VerificationResult.match(
                        GuessType.TITLE,
                        track.getTitle(),
                        titleMatch.distance(),
                        titleMatch.similarity()
                );
            }
        }

        // 2. Vérification contre l'Artiste (si non encore trouvé)
        if (!artistAlreadyDiscovered) {
            String artist = (track.getArtist() != null) ? track.getArtist() : track.getNormalizedArtist();
            String normalizedArtist = StringNormalizer.normalize(artist);

            // Collecter l'ensemble des artistes alternatifs et participants (feat, collaboration)
            Set<String> allArtistAlternatives = new LinkedHashSet<>();
            if (track.getAltArtists() != null) {
                allArtistAlternatives.addAll(track.getAltArtists());
            }

            // Extraire les artistes en feat / collaboration depuis l'artiste (ex: "David Guetta feat. Sia" -> "Sia")
            allArtistAlternatives.addAll(StringNormalizer.extractFeaturedArtists(track.getArtist()));

            // Extraire les artistes en feat depuis le titre officiel (ex: "Titanium (feat. Sia)" -> "Sia")
            allArtistAlternatives.addAll(StringNormalizer.extractFeaturedArtists(track.getTitle()));
            if (track.getAltTitles() != null) {
                for (String altTitle : track.getAltTitles()) {
                    allArtistAlternatives.addAll(StringNormalizer.extractFeaturedArtists(altTitle));
                }
            }

            MatchCandidate artistMatch = checkCandidate(normalizedGuess, normalizedArtist, new ArrayList<>(allArtistAlternatives), true);
            if (artistMatch.matched()) {
                String matchedDisplayName = (artistMatch.matchedName() != null && !artistMatch.matchedName().isBlank())
                        ? artistMatch.matchedName()
                        : track.getArtist();
                return VerificationResult.match(
                        GuessType.ARTIST,
                        matchedDisplayName,
                        artistMatch.distance(),
                        artistMatch.similarity()
                );
            }
        }

        return VerificationResult.notMatched();
    }

    private MatchCandidate checkCandidate(String normalizedGuess, String normalizedTarget, List<String> alternatives, boolean isArtist) {
        // Test direct et tolérant sur la cible principale
        MatchCandidate primary = computeMatch(normalizedGuess, normalizedTarget);
        if (primary.matched()) {
            return primary;
        }

        // Test par mot-clé isolé, sous-phrase ou acronyme sur la cible principale
        MatchCandidate tokenMatch = checkTokenOrSubphraseMatch(normalizedGuess, normalizedTarget, isArtist);
        if (tokenMatch.matched()) {
            return tokenMatch;
        }

        // Test sur les variantes alternatives si renseignées (alias, feats, etc.)
        if (alternatives != null) {
            for (String alt : alternatives) {
                if (alt == null || alt.isBlank()) continue;
                String normalizedAlt = StringNormalizer.normalize(alt);
                if (normalizedAlt.isBlank()) continue;

                MatchCandidate candidate = computeMatch(normalizedGuess, normalizedAlt);
                if (candidate.matched()) {
                    return new MatchCandidate(true, candidate.distance(), candidate.similarity(), alt);
                }
                MatchCandidate altToken = checkTokenOrSubphraseMatch(normalizedGuess, normalizedAlt, isArtist);
                if (altToken.matched()) {
                    return new MatchCandidate(true, altToken.distance(), altToken.similarity(), alt);
                }
            }
        }

        return new MatchCandidate(false, Integer.MAX_VALUE, 0.0);
    }

    public MatchCandidate computeMatch(String guess, String target) {
        if (guess.isBlank() || target.isBlank()) {
            return new MatchCandidate(false, Integer.MAX_VALUE, 0.0);
        }

        // A. Égalité stricte
        if (guess.equals(target)) {
            return new MatchCandidate(true, 0, 1.0);
        }

        // B. Égalité sans espaces ("big flo" == "bigflo", "ac dc" == "acdc")
        String noSpaceGuess = StringNormalizer.stripSpaces(guess);
        String noSpaceTarget = StringNormalizer.stripSpaces(target);
        if (!noSpaceGuess.isBlank() && noSpaceGuess.equals(noSpaceTarget)) {
            return new MatchCandidate(true, 0, 1.0);
        }

        // C. Égalité sans conjonctions ni espaces ("bigflo et oli" == "big flo & oli" == "bigflo oli")
        String conjFreeGuess = StringNormalizer.stripConjunctionsAndSpaces(guess);
        String conjFreeTarget = StringNormalizer.stripConjunctionsAndSpaces(target);
        if (!conjFreeGuess.isBlank() && conjFreeGuess.equals(conjFreeTarget)) {
            return new MatchCandidate(true, 0, 1.0);
        }

        // D. Distance de Damerau-Levenshtein textuelle
        int distance = damerauLevenshteinDistance(guess, target);
        int maxLen = Math.max(guess.length(), target.length());
        double similarity = 1.0 - ((double) distance / maxLen);
        int maxAllowedDistance = resolveMaxTolerance(target.length());

        if (distance <= maxAllowedDistance || similarity >= 0.80) {
            return new MatchCandidate(true, distance, similarity);
        }

        // E. Distance sans espaces (ex: une faute de frappe avec espacement libre)
        if (noSpaceGuess.length() >= 4 && noSpaceTarget.length() >= 4) {
            int noSpaceDist = damerauLevenshteinDistance(noSpaceGuess, noSpaceTarget);
            int noSpaceMaxTol = resolveMaxTolerance(noSpaceTarget.length());
            if (noSpaceDist <= noSpaceMaxTol) {
                double noSpaceSim = 1.0 - ((double) noSpaceDist / Math.max(noSpaceGuess.length(), noSpaceTarget.length()));
                return new MatchCandidate(true, noSpaceDist, noSpaceSim);
            }
        }

        // F. Empreinte phonétique ("muzz" == "muse", "emynem" == "eminem", "chaquira" == "shakira")
        String phoneticGuess = PhoneticNormalizer.phoneticCode(guess);
        String phoneticTarget = PhoneticNormalizer.phoneticCode(target);
        if (!phoneticGuess.isBlank() && !phoneticTarget.isBlank()) {
            if (phoneticGuess.equals(phoneticTarget)) {
                return new MatchCandidate(true, 0, 1.0);
            }
            int pDist = damerauLevenshteinDistance(phoneticGuess, phoneticTarget);
            int pMaxTol = resolveMaxTolerance(phoneticTarget.length());
            if (pDist <= pMaxTol) {
                double pSim = 1.0 - ((double) pDist / Math.max(phoneticGuess.length(), phoneticTarget.length()));
                return new MatchCandidate(true, pDist, pSim);
            }
        }

        return new MatchCandidate(false, distance, similarity);
    }

    /**
     * Valide les cas de saisie partielle, sous-mots, acronymes ou phrases contenues.
     * Ex: "ntm" dans "supreme ntm", "goldman" dans "jean-jacques goldman", "teen spirit" dans "smells like teen spirit"
     */
    public MatchCandidate checkTokenOrSubphraseMatch(String guess, String target, boolean isArtist) {
        if (guess.length() < 3 || target.length() < 3) {
            return new MatchCandidate(false, Integer.MAX_VALUE, 0.0);
        }

        if (STOP_WORDS.contains(guess)) {
            return new MatchCandidate(false, Integer.MAX_VALUE, 0.0);
        }

        String[] targetWords = target.split("\\s+");

        // 1. Acronyme de la cible (ex: "soad" pour "system of a down", "ratm" pour "rage against the machine", "bep" pour "black eyed peas", "rhcp" pour "red hot chili peppers")
        if (targetWords.length >= 2 && guess.length() >= 3) {
            StringBuilder acronym = new StringBuilder();
            for (String w : targetWords) {
                if (!w.isBlank()) {
                    acronym.append(w.charAt(0));
                }
            }
            if (guess.equalsIgnoreCase(acronym.toString())) {
                return new MatchCandidate(true, 0, 1.0);
            }

            StringBuilder acronymNoStop = new StringBuilder();
            for (String w : targetWords) {
                if (!w.isBlank() && !STOP_WORDS.contains(w)) {
                    acronymNoStop.append(w.charAt(0));
                }
            }
            if (acronymNoStop.length() >= 3 && guess.equalsIgnoreCase(acronymNoStop.toString())) {
                return new MatchCandidate(true, 0, 1.0);
            }
        }

        // 2. Token match : deviner un mot-clé unique et significatif
        // Ex: "ntm" dans "supreme ntm", "goldman" dans "jean jacques goldman", "stones" dans "rolling stones"
        if (targetWords.length >= 2) {
            for (String w : targetWords) {
                if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                    // Si c'est un prénom très commun, on ne le valide pas seul pour un artiste (sauf alias explicite)
                    if (isArtist && COMMON_FIRST_NAMES.contains(w)) {
                        continue;
                    }

                    // Correspondance exacte sur le mot
                    if (guess.equals(w)) {
                        return new MatchCandidate(true, 0, 1.0);
                    }

                    // Correspondance phonétique sur le mot
                    String pGuess = PhoneticNormalizer.phoneticCode(guess);
                    String pW = PhoneticNormalizer.phoneticCode(w);
                    if (!pGuess.isBlank() && pGuess.equals(pW)) {
                        return new MatchCandidate(true, 0, 1.0);
                    }

                    // Tolérance faute de frappe sur mot significatif (>= 5 lettres)
                    if (w.length() >= 5) {
                        int dist = damerauLevenshteinDistance(guess, w);
                        if (dist <= 1) {
                            return new MatchCandidate(true, dist, 0.9);
                        }
                    }
                }
            }
        }

        // 3. Sous-phrase : si la saisie correspond à une sous-partie contiguë
        // Ex: "red hot" dans "red hot chili peppers", "teen spirit" dans "smells like teen spirit", "rage against" dans "rage against the machine"
        if ((guess.contains(" ") || guess.length() >= 5) && !(isArtist && COMMON_FIRST_NAMES.contains(guess))) {
            if (target.startsWith(guess + " ") || target.endsWith(" " + guess) || target.contains(" " + guess + " ")) {
                return new MatchCandidate(true, 0, 0.95);
            }
        }

        // 4. L'inverse : la cible est contenue dans la saisie du joueur
        // Ex: joueur tape "c est muse" ou "le groupe supreme ntm" ou "chanson dommage"
        if (guess.startsWith(target + " ") || guess.endsWith(" " + target) || guess.contains(" " + target + " ")) {
            return new MatchCandidate(true, 0, 0.95);
        }

        return new MatchCandidate(false, Integer.MAX_VALUE, 0.0);
    }

    /**
     * Détermine la tolérance maximale de fautes selon la longueur du mot cible.
     */
    public static int resolveMaxTolerance(int targetLength) {
        if (targetLength <= 4) {
            return 0; // Ex: "U2", "Air", "Yes", "Muse" -> aucune tolérance textuelle arbitraire (la phonétique reste active)
        } else if (targetLength <= 7) {
            return 1; // Ex: "Queen", "Adele", "Johnny" -> 1 faute autorisée
        } else if (targetLength <= 11) {
            return 2; // Ex: "Coldplay", "Nirvana" -> 2 fautes autorisées
        } else {
            return 3; // Ex: "Michael Jackson" -> 3 fautes autorisées
        }
    }

    /**
     * Calcul de la distance de Damerau-Levenshtein (transposition de deux lettres adjacentes incluse).
     */
    public static int damerauLevenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();

        int[][] dp = new int[lenA + 1][lenB + 1];

        for (int i = 0; i <= lenA; i++) dp[i][0] = i;
        for (int j = 0; j <= lenB; j++) dp[0][j] = j;

        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                // Suppression, Insertion, Substitution
                int minCost = Math.min(dp[i - 1][j] + 1,
                              Math.min(dp[i][j - 1] + 1,
                                       dp[i - 1][j - 1] + cost));

                // Transposition (inversion de deux lettres côte à côte)
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    minCost = Math.min(minCost, dp[i - 2][j - 2] + cost);
                }

                dp[i][j] = minCost;
            }
        }

        return dp[lenA][lenB];
    }

    public record MatchCandidate(boolean matched, int distance, double similarity, String matchedName) {
        public MatchCandidate(boolean matched, int distance, double similarity) {
            this(matched, distance, similarity, null);
        }
    }
}
