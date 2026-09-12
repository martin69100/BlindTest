package com.blindtest.lobby.controller;

import com.blindtest.lobby.dto.CreateLobbyRequest;
import com.blindtest.lobby.dto.JoinLobbyRequest;
import com.blindtest.lobby.dto.UpdateLobbySettingsRequest;
import com.blindtest.lobby.model.Lobby;
import com.blindtest.lobby.service.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;

    @PostMapping("/create")
    public ResponseEntity<Lobby> createLobby(@RequestBody CreateLobbyRequest request) {
        Lobby lobby = lobbyService.createLobby(
                request.getHostId(),
                request.getHostName(),
                request.getAvatarUrl(),
                request.getElo(),
                request.getThemeId(),
                request.getThemeName(),
                request.getRoundsCount()
        );
        return ResponseEntity.ok(lobby);
    }

    @PostMapping("/join")
    public ResponseEntity<Lobby> joinLobby(@RequestBody JoinLobbyRequest request) {
        Lobby lobby = lobbyService.joinLobby(
                request.getCode(),
                request.getUserId(),
                request.getDisplayName(),
                request.getAvatarUrl(),
                request.getElo()
        );
        return ResponseEntity.ok(lobby);
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<Map<String, Object>> leaveLobby(@PathVariable String code, @RequestParam UUID userId) {
        Lobby lobby = lobbyService.leaveLobby(code, userId);
        return ResponseEntity.ok(Map.of(
                "status", "LEFT",
                "code", code,
                "remainingParticipants", lobby != null ? lobby.getParticipants().size() : 0
        ));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Lobby> getLobby(@PathVariable String code) {
        Lobby lobby = lobbyService.getLobby(code);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lobby);
    }

    @PostMapping("/{code}/settings")
    public ResponseEntity<Lobby> updateSettings(@PathVariable String code,
                                                @RequestBody UpdateLobbySettingsRequest request) {
        Lobby lobby = lobbyService.updateSettings(
                code,
                request.getRequestingUserId(),
                request.getThemeId(),
                request.getThemeName(),
                request.getRoundsCount(),
                request.getGameMode(),
                request.getTeamMode()
        );
        return ResponseEntity.ok(lobby);
    }

    @PostMapping("/{code}/team")
    public ResponseEntity<Lobby> switchTeam(@PathVariable String code,
                                            @RequestParam UUID userId,
                                            @RequestParam String team) {
        Lobby lobby = lobbyService.switchTeam(code, userId, team);
        return ResponseEntity.ok(lobby);
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<Lobby> startGame(@PathVariable String code, @RequestParam UUID userId) {
        Lobby lobby = lobbyService.startGame(code, userId);
        return ResponseEntity.ok(lobby);
    }

    @PostMapping("/{code}/return")
    public ResponseEntity<Lobby> returnToLobby(@PathVariable String code, @RequestParam UUID userId) {
        Lobby lobby = lobbyService.returnToLobby(code, userId);
        return ResponseEntity.ok(lobby);
    }

    @PostMapping("/{code}/custom-playlist")
    public ResponseEntity<Lobby> setCustomPlaylist(@PathVariable String code,
                                                   @RequestParam UUID userId,
                                                   @RequestBody Map<String, String> body) {
        String playlistUrl = body != null ? body.get("playlistUrl") : null;
        Lobby lobby = lobbyService.setCustomPlaylist(code, userId, playlistUrl);
        return ResponseEntity.ok(lobby);
    }

    @DeleteMapping("/{code}/custom-playlist")
    public ResponseEntity<Lobby> clearCustomPlaylist(@PathVariable String code,
                                                     @RequestParam UUID userId) {
        Lobby lobby = lobbyService.clearCustomPlaylist(code, userId);
        return ResponseEntity.ok(lobby);
    }
}
