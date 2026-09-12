package com.blindtest.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardUserDto {
    private UUID id;
    private String displayName;
    private String avatarUrl;
    private Integer elo;
    private Instant createdAt;
    private Instant lastActiveAt;
}
