package com.blindtest.game.fuzzymatch;

import com.blindtest.game.entity.MatchRound.GuessType;

public record VerificationResult(
        boolean matched,
        GuessType guessType,
        String matchedName,
        int distance,
        double similarityRatio
) {
    public static VerificationResult notMatched() {
        return new VerificationResult(false, GuessType.NONE, null, Integer.MAX_VALUE, 0.0);
    }

    public static VerificationResult match(GuessType type, String matchedName, int distance, double similarity) {
        return new VerificationResult(true, type, matchedName, distance, similarity);
    }
}
