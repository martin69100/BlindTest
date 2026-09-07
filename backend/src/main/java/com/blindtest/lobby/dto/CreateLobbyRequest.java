package com.blindtest.lobby.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateLobbyRequest {
    private UUID hostId;
    private String hostName;
    private String avatarUrl;
    private Integer elo;
    private UUID themeId;
    private String themeName;
    private Integer roundsCount;
}
