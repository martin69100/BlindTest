package com.blindtest.theme.repository;

import com.blindtest.theme.entity.Theme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThemeRepository extends JpaRepository<Theme, UUID> {

    Optional<Theme> findByCode(String code);

    List<Theme> findByIsActiveTrueOrderByCreatedAtAsc();
}
