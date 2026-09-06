package com.blindtest.game.fuzzymatch;

import java.util.regex.Pattern;

public final class PhoneticNormalizer {

    private static final Pattern REPEATED_LETTERS = Pattern.compile("([a-z])\\1+");

    private PhoneticNormalizer() {}

    /**
     * Calcule l'empreinte phonétique simplifiée d'une chaîne normalisée.
     * Permet d'accepter les variantes phonétiques courantes en blind test :
     * "muzz" -> "mus", "muse" -> "mus"
     * "emynem" -> "eminem"
     * "chaquira" -> "shakira"
     * "telephone" -> "telefone"
     */
    public static String phoneticCode(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String s = input.toLowerCase();

        // 1. Ph -> f, Y -> i, W -> v
        s = s.replaceAll("ph", "f");
        s = s.replaceAll("y", "i");
        s = s.replaceAll("w", "v");

        // 2. Équivalence phonétique z <-> s
        s = s.replaceAll("z", "s");

        // 3. Équivalence k / q / qu / ck / hard c -> k
        s = s.replaceAll("ck|qu|q", "k");
        s = s.replaceAll("c(?=[ei])", "s"); // c doux (ex: "celine" -> "selin")
        s = s.replaceAll("c", "k");          // c dur (ex: "acdc" -> "akdk")

        // 4. Réduction des consonnes et voyelles répétées ("zz"->"z", "ss"->"s", etc.)
        s = REPEATED_LETTERS.matcher(s).replaceAll("$1");

        // 5. Sons voyelles fréquents : eau/aux -> o, ai/ei -> e
        s = s.replaceAll("eau|aux", "o");
        s = s.replaceAll("ai|ei", "e");

        // 6. Suppression du 'e' muet final (mots de plus de 3 lettres, ex: "muse" -> "mus")
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.length() > 3 && w.endsWith("e")) {
                w = w.substring(0, w.length() - 1);
            }
            if (i > 0) sb.append(" ");
            sb.append(w);
        }

        return sb.toString().trim();
    }
}
