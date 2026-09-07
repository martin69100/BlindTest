package com.blindtest.track.repository;

import com.blindtest.track.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackRepository extends JpaRepository<Track, UUID> {

    Optional<Track> findByDeezerId(Long deezerId);

    List<Track> findByThemeId(UUID themeId);

    @Query(value = "SELECT * FROM tracks WHERE theme_id = :themeId AND is_active = true ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Track> findRandomTracksByTheme(@Param("themeId") UUID themeId, @Param("limit") int limit);

    @Query(value = "SELECT * FROM tracks WHERE is_active = true ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Track> findRandomTracks(@Param("limit") int limit);

    long countByThemeIdAndIsActiveTrue(UUID themeId);
}
