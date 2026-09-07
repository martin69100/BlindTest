package com.blindtest.lobby.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateLobbySettingsRequest {
    private UUID requestingUserId;
    private UUID themeId;
    private String themeName;
    private Integer roundsCount;
}
