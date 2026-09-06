package com.blindtest.user.repository;

import com.blindtest.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u ORDER BY u.elo DESC")
    List<User> findTopPlayers(Pageable pageable);
}
