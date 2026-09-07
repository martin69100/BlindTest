package com.blindtest.matchmaking.controller;

import com.blindtest.matchmaking.model.MatchmakingStats;
import com.blindtest.matchmaking.service.MatchmakingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matchmaking")
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    @Data
    public static class JoinQueueRequest {
        private UUID userId;
        private UUID preferredThemeId;
    }

    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinQueue(@RequestBody JoinQueueRequest request) {
        boolean joined = matchmakingService.joinQueue(request.getUserId(), request.getPreferredThemeId());
        return ResponseEntity.ok(Map.of(
                "status", joined ? "QUEUED" : "FAILED",
                "userId", request.getUserId()
        ));
    }

    @PostMapping("/leave")
    public ResponseEntity<Map<String, Object>> leaveQueue(@RequestParam UUID userId) {
        boolean removed = matchmakingService.leaveQueue(userId);
        return ResponseEntity.ok(Map.of(
                "status", removed ? "REMOVED" : "NOT_IN_QUEUE",
                "userId", userId
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam UUID userId) {
        boolean inQueue = matchmakingService.isInQueue(userId);
        return ResponseEntity.ok(Map.of(
                "inQueue", inQueue,
                "userId", userId
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<MatchmakingStats> getStats() {
        return ResponseEntity.ok(matchmakingService.getStats());
    }
}
