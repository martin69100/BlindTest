package com.blindtest.game.repository;

import com.blindtest.game.entity.Match;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Query("SELECT m FROM Match m WHERE (m.player1.id = :userId OR m.player2.id = :userId) AND m.status = 'COMPLETED' ORDER BY m.startedAt DESC")
    List<Match> findRecentMatchesByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Match m WHERE (m.player1.id = :userId OR m.player2.id = :userId) AND m.status = 'COMPLETED'")
    long countCompletedMatchesByUserId(@Param("userId") UUID userId);
}
