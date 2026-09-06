package com.blindtest.theme.controller;

import com.blindtest.theme.entity.Theme;
import com.blindtest.theme.repository.ThemeRepository;
import com.blindtest.track.repository.TrackRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/themes")
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeRepository themeRepository;
    private final TrackRepository trackRepository;

    @Data
    @Builder
    public static class ThemeResponseDto {
        private UUID id;
        private String code;
        private String name;
        private String description;
        private String iconUrl;
        private long trackCount;
    }

    @GetMapping
    public ResponseEntity<List<ThemeResponseDto>> getAllActiveThemes() {
        List<Theme> themes = themeRepository.findByIsActiveTrueOrderByCreatedAtAsc();
        List<ThemeResponseDto> result = themes.stream().map(theme -> {
            long count = trackRepository.countByThemeIdAndIsActiveTrue(theme.getId());
            return ThemeResponseDto.builder()
                    .id(theme.getId())
                    .code(theme.getCode())
                    .name(theme.getName())
                    .description(theme.getDescription())
                    .iconUrl(theme.getIconUrl())
                    .trackCount(count)
                    .build();
        }).toList();

        return ResponseEntity.ok(result);
    }
}
