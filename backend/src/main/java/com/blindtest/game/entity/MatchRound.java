package com.blindtest.game.entity;

import com.blindtest.track.entity.Track;
import com.blindtest.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_rounds", uniqueConstraints = {
    @UniqueConstraint(name = "uq_match_round", columnNames = {"match_id", "round_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchRound {

    public enum GuessType {
        NONE,
        TITLE,
        ARTIST
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buzzer_user_id")
    private User buzzerUser;

    @Column(name = "reaction_time_ms")
    private Integer reactionTimeMs;

    @Column(name = "first_guess")
    private String firstGuess;

    @Column(name = "first_guess_correct")
    @Builder.Default
    private Boolean firstGuessCorrect = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_guess_type", length = 20)
    private GuessType firstGuessType;

    @Column(name = "bonus_guess")
    private String bonusGuess;

    @Column(name = "bonus_guess_correct")
    @Builder.Default
    private Boolean bonusGuessCorrect = false;

    @Column(name = "points_awarded", nullable = false)
    @Builder.Default
    private Integer pointsAwarded = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
