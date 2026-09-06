package com.blindtest.user.controller;

import com.blindtest.game.entity.Match;
import com.blindtest.game.repository.MatchRepository;
import com.blindtest.theme.entity.UserThemeStats;
import com.blindtest.theme.repository.UserThemeStatsRepository;
import com.blindtest.user.entity.User;
import com.blindtest.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserThemeStatsRepository themeStatsRepository;
    private final MatchRepository matchRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{userId}/stats")
    public ResponseEntity<List<UserThemeStats>> getUserThemeStats(@PathVariable UUID userId) {
        List<UserThemeStats> stats = themeStatsRepository.findByUserId(userId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<Match>> getUserMatchHistory(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "15") int limit
    ) {
        List<Match> matches = matchRepository.findRecentMatchesByUserId(userId, PageRequest.of(0, limit));
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<User>> getLeaderboard(@RequestParam(defaultValue = "20") int limit) {
        List<User> top = userRepository.findTopPlayers(PageRequest.of(0, limit));
        return ResponseEntity.ok(top);
    }
}
