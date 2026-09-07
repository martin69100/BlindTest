package com.blindtest.lobby.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class JoinLobbyRequest {
    private String code;
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private Integer elo;
}
