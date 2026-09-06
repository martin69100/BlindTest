package com.blindtest.track.controller;

import com.blindtest.track.service.DeezerClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final DeezerClientService deezerClientService;

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importTracks(
            @RequestParam String themeCode,
            @RequestParam String query,
            @RequestParam(defaultValue = "30") int limit
    ) {
        int imported = deezerClientService.importTracksForTheme(themeCode, query, limit);
        return ResponseEntity.ok(Map.of(
                "themeCode", themeCode,
                "importedCount", imported,
                "status", "SUCCESS"
        ));
    }

    @PostMapping("/seed-all")
    public ResponseEntity<Map<String, String>> seedAllThemes() {
        deezerClientService.seedInitialTracksIfEmpty();
        return ResponseEntity.ok(Map.of("message", "Importation des thèmes déclenchée avec succès."));
    }

    @PostMapping("/reseed-curated")
    public ResponseEntity<Map<String, Object>> reseedCuratedCatalogue() {
        Map<String, Object> result = deezerClientService.reseedCuratedCatalogue();
        return ResponseEntity.ok(result);
    }
}
