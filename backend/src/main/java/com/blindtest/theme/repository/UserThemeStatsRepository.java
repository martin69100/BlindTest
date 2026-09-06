package com.blindtest.theme.repository;

import com.blindtest.theme.entity.UserThemeStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserThemeStatsRepository extends JpaRepository<UserThemeStats, UUID> {

    Optional<UserThemeStats> findByUserIdAndThemeId(UUID userId, UUID themeId);

    List<UserThemeStats> findByUserId(UUID userId);
}
