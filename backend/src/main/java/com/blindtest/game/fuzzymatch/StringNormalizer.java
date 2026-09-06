package com.blindtest.game.fuzzymatch;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
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

    // Regex pour extraire les artistes en featuring entre parenthèses ou crochets : (feat. Sia) ou [ft. Rihanna]
    private static final Pattern FEAT_PAREN_PATTERN = Pattern.compile(
            "(?i)[(\\[]\\s*(?:feat\\.?|ft\\.?|featuring|with|avec)\\s+([^)\\]]+)[)\\]]"
    );

    // Regex pour extraire les artistes en featuring hors parenthèses : "David Guetta feat. Sia"
    private static final Pattern FEAT_INLINE_PATTERN = Pattern.compile(
            "(?i)(?:^|\\s+)(?:feat\\.?|ft\\.?|featuring|with|avec)\\s+(.+)$"
    );

    // Regex pour découper une liste de collaborateurs ("Sia & Akon", "Pharrell Williams, Nile Rodgers")
    private static final Pattern COLLAB_SPLIT_PATTERN = Pattern.compile(
            "(?i)\\s*(?:&|\\band\\b|\\bet\\b|,|\\bx\\b|/)\\s*"
    );

    /**
     * Extrait tous les artistes individuels présents dans une chaîne d'artiste ou de titre.
     * Ex: "David Guetta feat. Sia" -> ["Sia"],
     *     "Titanium (feat. Sia)" -> ["Sia"],
     *     "Major Lazer & DJ Snake feat. MØ" -> ["Major Lazer", "DJ Snake", "MØ"],
     *     "Get Lucky (feat. Pharrell Williams & Nile Rodgers)" -> ["Pharrell Williams", "Nile Rodgers"]
     */
    public static Set<String> extractFeaturedArtists(String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> artists = new LinkedHashSet<>();

        // 1. Détection dans les parenthèses / crochets (ex: "Titanium (feat. Sia)")
        Matcher mParen = FEAT_PAREN_PATTERN.matcher(input);
        while (mParen.find()) {
            String featBlock = mParen.group(1);
            addSplitArtists(featBlock, artists);
        }

        // 2. Détection hors parenthèses (ex: "David Guetta feat. Sia")
        Matcher mInline = FEAT_INLINE_PATTERN.matcher(input);
        if (mInline.find()) {
            String featBlock = mInline.group(1);
            featBlock = featBlock.replaceAll("[)\\]]", "");
            addSplitArtists(featBlock, artists);
        }

        // 3. Détection des collaborations par conjonction (&, x, and, et, virgule)
        // ex: "David Guetta & Sia" -> "David Guetta", "Sia"
        if (input.contains("&") || input.contains(" x ") || input.contains(" X ") || input.contains(",")) {
            String[] parts = COLLAB_SPLIT_PATTERN.split(input);
            if (parts.length > 1) {
                for (String p : parts) {
                    String clean = p.replaceAll("(?i)[(\\[].*?[)\\]]", "")
                            .replaceAll("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|with|avec)\\s+.*$", "")
                            .trim();
                    if (clean.length() >= 2 && !isStopOrNoise(clean)) {
                        artists.add(clean);
                    }
                }
            }
        }

        return artists;
    }

    private static void addSplitArtists(String featBlock, Set<String> artists) {
        if (featBlock == null || featBlock.isBlank()) return;
        String[] parts = COLLAB_SPLIT_PATTERN.split(featBlock);
        for (String p : parts) {
            String clean = p.replaceAll("(?i)[(\\[].*?[)\\]]", "").trim();
            if (clean.length() >= 2 && !isStopOrNoise(clean)) {
                artists.add(clean);
            }
        }
    }

    private static boolean isStopOrNoise(String word) {
        String lower = word.toLowerCase();
        return lower.equals("remix") || lower.equals("remaster") || lower.equals("remastered")
                || lower.equals("live") || lower.equals("radio edit") || lower.equals("version")
                || lower.equals("feat") || lower.equals("ft");
    }
}
