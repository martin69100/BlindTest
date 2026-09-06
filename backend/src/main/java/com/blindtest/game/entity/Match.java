package com.blindtest.game.entity;

import com.blindtest.theme.entity.Theme;
import com.blindtest.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    public enum MatchStatus {
        IN_PROGRESS,
        COMPLETED,
        ABANDONED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player1_id", nullable = false)
    private User player1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player2_id", nullable = false)
    private User player2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "player1_score", nullable = false)
    @Builder.Default
    private Integer player1Score = 0;

    @Column(name = "player2_score", nullable = false)
    @Builder.Default
    private Integer player2Score = 0;

    @Column(name = "player1_elo_before", nullable = false)
    private Integer player1EloBefore;

    @Column(name = "player2_elo_before", nullable = false)
    private Integer player2EloBefore;

    @Column(name = "player1_elo_change", nullable = false)
    @Builder.Default
    private Integer player1EloChange = 0;

    @Column(name = "player2_elo_change", nullable = false)
    @Builder.Default
    private Integer player2EloChange = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_id")
    private Theme theme;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MatchStatus status = MatchStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;
}
