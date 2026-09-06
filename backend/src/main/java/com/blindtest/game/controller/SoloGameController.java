package com.blindtest.game.controller;

import com.blindtest.game.model.GameSession;
import com.blindtest.game.service.GameEngineService;
import com.blindtest.user.entity.User;
import com.blindtest.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/games/solo")
@RequiredArgsConstructor
public class SoloGameController {

    private final GameEngineService gameEngineService;
    private final UserRepository userRepository;

    @Data
    public static class StartSoloRequest {
        private UUID userId;
        private UUID themeId; // Optionnel (null pour "Tous thèmes")
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSoloSession(@RequestBody StartSoloRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

        GameSession session = gameEngineService.createVersusMatch(
                user.getId(),
                user.getDisplayName(),
                null,
                null,
                request.getThemeId()
        );

        return ResponseEntity.ok(Map.of(
                "gameId", session.getGameId(),
                "player1Id", user.getId().toString(),
                "player1Name", user.getDisplayName(),
                "totalRounds", session.getPlaylist().size(),
                "themeId", request.getThemeId() != null ? request.getThemeId() : "ALL"
        ));
    }
}
