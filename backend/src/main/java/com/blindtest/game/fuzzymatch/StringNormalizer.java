package com.blindtest.game.fuzzymatch;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class StringNormalizer {

    // Regex pour supprimer les annotations entre parenthèses ou crochets (feat, remix, remaster, live, etc.)
    private static final Pattern ANNOTATIONS_PATTERN = Pattern.compile(
            "(?i)[(\\[]\\s*(feat\\.?|ft\\.?|remaster|remix|version|live|radio edit|original|deluxe|bonus).*?[)\\]]"
    );

    // Regex pour supprimer les "feat." ou "ft." hors parenthèses (ex: "Eminem ft. Rihanna")
    private static final Pattern INLINE_FEAT_PATTERN = Pattern.compile(
            "(?i)\\s+(feat\\.?|ft\\.?)\\s+.*$"
    );

    // Regex pour uniformiser les conjonctions et symboles (&, +, and -> et)
    private static final Pattern CONJUNCTIONS_PATTERN = Pattern.compile("(?i)[&+]|\\band\\b");

    // Regex pour supprimer la ponctuation et caractères spéciaux non alphanumériques
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");

    // Regex pour réduire les espaces multiples
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    // Regex pour supprimer les articles initiaux (anglais et français)
    private static final Pattern INITIAL_ARTICLES_PATTERN = Pattern.compile(
            "^(the|les|le|la|l|un|une|des|a|an)\\s+"
    );

    private StringNormalizer() {}

    /**
     * Normalise complètement une chaîne pour comparaison souple (Fuzzy Matching).
     *
     * @param input La chaîne brute (titre ou artiste)
     * @return La chaîne normalisée canonique
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1. Supprimer les annotations feat, remix, remaster
        String cleaned = ANNOTATIONS_PATTERN.matcher(input).replaceAll(" ");
        cleaned = INLINE_FEAT_PATTERN.matcher(cleaned).replaceAll(" ");

        // 2. Uniformiser les conjonctions (&, +, and -> et)
        cleaned = CONJUNCTIONS_PATTERN.matcher(cleaned).replaceAll(" et ");

        // 3. Supprimer les diacritiques (accents) : Céline -> Celine
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        cleaned = cleaned.replaceAll("\\p{M}", "");

        // 4. Remplacer les caractères spéciaux par des espaces (ex: "P!nk" -> "P nk", "AC/DC" -> "AC DC")
        cleaned = SPECIAL_CHARS_PATTERN.matcher(cleaned).replaceAll(" ");

        // 5. Mettre en minuscule et réduire les espaces
        cleaned = cleaned.toLowerCase().trim();
        cleaned = MULTI_SPACE_PATTERN.matcher(cleaned).replaceAll(" ");

        // 6. Supprimer les articles initiaux fréquents ("the beatles" -> "beatles")
        cleaned = INITIAL_ARTICLES_PATTERN.matcher(cleaned).replaceAll("");

        return cleaned.trim();
    }

    /**
     * Supprime tous les espaces blancs pour comparaison insensible aux espaces.
     * Ex: "big flo" -> "bigflo", "ac dc" -> "acdc"
     */
    public static String stripSpaces(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "");
    }

    /**
     * Supprime les conjonctions ("et", "and") ainsi que les espaces pour comparaison canonique.
     * Ex: "bigflo et oli" -> "bigfooli", "big flo & oli" -> "bigfooli", "bigflo oli" -> "bigfooli"
     */
    public static String stripConjunctionsAndSpaces(String s) {
        if (s == null) return "";
        String withoutConj = s.replaceAll("(?i)\\b(et|and)\\b", "");
        return stripSpaces(withoutConj);
    }
}
