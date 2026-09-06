package com.blindtest.auth;

import com.blindtest.theme.entity.Theme;
import com.blindtest.theme.entity.UserThemeStats;
import com.blindtest.theme.repository.ThemeRepository;
import com.blindtest.theme.repository.UserThemeStatsRepository;
import com.blindtest.user.entity.User;
import com.blindtest.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final ThemeRepository themeRepository;
    private final UserThemeStatsRepository statsRepository;

    @Data
    public static class DevLoginRequest {
        private String displayName;
        private String email;
        private String avatarUrl;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        if (principal != null) {
            String googleId = principal.getAttribute("sub");
            return userRepository.findByGoogleId(googleId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        // Support du token (Bearer header ou paramètre URL pour requêtes cross-origin SPA)
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            token = tokenParam.trim();
        }

        if (token != null && !token.isEmpty()) {
            Optional<User> byGoogleId = userRepository.findByGoogleId(token);
            if (byGoogleId.isPresent()) {
                return ResponseEntity.ok(byGoogleId.get());
            }
            try {
                UUID userId = UUID.fromString(token);
                Optional<User> byId = userRepository.findById(userId);
                if (byId.isPresent()) {
                    return ResponseEntity.ok(byId.get());
                }
            } catch (IllegalArgumentException ignored) {}
        }

        return ResponseEntity.status(401).body(Map.of("error", "Non authentifié"));
    }

    /**
     * Endpoint d'authentification pour le développement local et les tests multi-joueurs.
     */
    @PostMapping("/dev-login")
    public ResponseEntity<User> devLogin(@RequestBody DevLoginRequest request) {
        String email = (request.getEmail() != null && !request.getEmail().isBlank())
                ? request.getEmail()
                : "dev_" + request.getDisplayName().toLowerCase().replaceAll("\\s+", "") + "@blindtest.local";

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .googleId("dev_" + UUID.randomUUID())
                    .email(email)
                    .displayName(request.getDisplayName() != null ? request.getDisplayName() : "Joueur Dev")
                    .avatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl() : "https://api.dicebear.com/7.x/bottts/svg?seed=" + email)
                    .elo(1000)
                    .createdAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .build();
            User saved = userRepository.save(newUser);

            List<Theme> themes = themeRepository.findAll();
            for (Theme t : themes) {
                UserThemeStats stats = UserThemeStats.builder()
                        .user(saved)
                        .theme(t)
                        .soloGamesPlayed(0)
                        .soloCorrectAnswers(0)
                        .versusCorrectAnswers(0)
                        .bonusCorrectAnswers(0)
                        .masteryPoints(0)
                        .updatedAt(Instant.now())
                        .build();
                statsRepository.save(stats);
            }
            return saved;
        });

        user.setLastActiveAt(Instant.now());
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }
}
