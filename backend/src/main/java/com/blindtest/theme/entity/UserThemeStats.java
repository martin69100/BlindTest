package com.blindtest.theme.entity;

import com.blindtest.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_theme_stats", uniqueConstraints = {
    @UniqueConstraint(name = "uq_user_theme", columnNames = {"user_id", "theme_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserThemeStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theme_id", nullable = false)
    private Theme theme;

    @Column(name = "solo_games_played", nullable = false)
    @Builder.Default
    private Integer soloGamesPlayed = 0;

    @Column(name = "solo_correct_answers", nullable = false)
    @Builder.Default
    private Integer soloCorrectAnswers = 0;

    @Column(name = "versus_correct_answers", nullable = false)
    @Builder.Default
    private Integer versusCorrectAnswers = 0;

    @Column(name = "bonus_correct_answers", nullable = false)
    @Builder.Default
    private Integer bonusCorrectAnswers = 0;

    @Column(name = "mastery_points", nullable = false)
    @Builder.Default
    private Integer masteryPoints = 0;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
