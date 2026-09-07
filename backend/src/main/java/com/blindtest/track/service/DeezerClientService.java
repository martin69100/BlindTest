package com.blindtest.track.service;

import com.blindtest.game.fuzzymatch.StringNormalizer;
import com.blindtest.theme.entity.Theme;
import com.blindtest.theme.repository.ThemeRepository;
import com.blindtest.track.dto.DeezerResponseDto;
import com.blindtest.track.entity.Track;
import com.blindtest.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeezerClientService {

    private final TrackRepository trackRepository;
    private final ThemeRepository themeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${blindtest.deezer.base-url:https://api.deezer.com}")
    private String deezerBaseUrl;

    @Value("${blindtest.deezer.theme-playlists.ANNEES_80:867825522}")
    private Long playlist80s;

    @Value("${blindtest.deezer.theme-playlists.ANNEES_60:908622995}")
    private Long playlist60s;

    @Value("${blindtest.deezer.theme-playlists.ROCK:1306931615}")
    private Long playlistRock;

    @Value("${blindtest.deezer.theme-playlists.RAP_FR:2918278382}")
    private Long playlistRapFr;

    @Value("${blindtest.deezer.theme-playlists.POP:1036183001}")
    private Long playlistPop;

    @Value("${blindtest.deezer.theme-playlists.ANNEES_2000:751764391}")
    private Long playlist2000s;

    private record CachedPreview(String url, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final java.util.Map<Long, CachedPreview> previewCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Garantit l'obtention d'un lien preview MP3 Deezer valide avec un token CDN actif (non expiré).
     */
    public String getFreshPreviewUrl(Track track) {
        if (track == null) return null;
        if (track.getDeezerId() == null) {
            return track.getPreviewUrl();
        }

        // 1. Vérifier le cache en mémoire (durée 45 minutes)
        CachedPreview cached = previewCache.get(track.getDeezerId());
        if (cached != null && !cached.isExpired()) {
            return cached.url();
        }

        // 2. Interroger Deezer API /track/{id} pour obtenir le token CDN fraîchement signé
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(deezerBaseUrl)
                    .build();

            DeezerResponseDto.DeezerTrackItem item = restClient.get()
                    .uri("/track/{id}", track.getDeezerId())
                    .retrieve()
                    .body(DeezerResponseDto.DeezerTrackItem.class);

            if (item != null && item.getPreview() != null && !item.getPreview().isBlank()) {
                String freshUrl = item.getPreview();
                previewCache.put(track.getDeezerId(), new CachedPreview(freshUrl, System.currentTimeMillis() + 45 * 60 * 1000L));
                track.setPreviewUrl(freshUrl);
                trackRepository.save(track);
                log.info("Lien Deezer rafraîchi pour trackId={} (titre: '{}') : {}", track.getId(), track.getTitle(), freshUrl);
                return freshUrl;
            }
        } catch (Exception e) {
            log.warn("Impossible de rafraîchir le lien Deezer pour trackId={} (deezerId={}): {}",
                    track.getId(), track.getDeezerId(), e.getMessage());
        }

        // 3. Recherche de secours sur Deezer si le trackId spécifique n'a plus de preview valide
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(deezerBaseUrl)
                    .build();

            String queryParam = String.format("artist:\"%s\" track:\"%s\"", track.getArtist(), track.getTitle());
            DeezerResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", queryParam)
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(DeezerResponseDto.class);

            if (response != null && response.getData() != null) {
                for (DeezerResponseDto.DeezerTrackItem fallbackItem : response.getData()) {
                    if (fallbackItem.getPreview() != null && !fallbackItem.getPreview().isBlank()) {
                        String freshUrl = fallbackItem.getPreview();
                        previewCache.put(fallbackItem.getId(), new CachedPreview(freshUrl, System.currentTimeMillis() + 45 * 60 * 1000L));
                        track.setDeezerId(fallbackItem.getId());
                        track.setPreviewUrl(freshUrl);
                        trackRepository.save(track);
                        log.info("Lien Deezer de secours trouvé pour trackId={} (titre: '{}') : {}", track.getId(), track.getTitle(), freshUrl);
                        return freshUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Recherche de secours Deezer échouée pour trackId={} (titre: '{}') : {}",
                    track.getId(), track.getTitle(), e.getMessage());
        }

        return track.getPreviewUrl();
    }

    /**
     * Recherche et importe des titres Deezer associés à un thème donné.
     *
     * @param themeCode Code du thème (ex: 'ANNEES_80', 'ROCK', etc.)
     * @param query     Mots-clés de recherche pour Deezer (ex: "80s hits", "rock classics")
     * @param limit     Nombre de morceaux à récupérer
     * @return Nombre de nouveaux titres enregistrés
     */
    @Transactional
    public int importTracksForTheme(String themeCode, String query, int limit) {
        Theme theme = themeRepository.findByCode(themeCode)
                .orElseThrow(() -> new IllegalArgumentException("Thème introuvable : " + themeCode));

        RestClient restClient = RestClient.builder()
                .baseUrl(deezerBaseUrl)
                .build();

        try {
            log.info("Appel Deezer API : recherche de morceaux pour le thème '{}' avec la requête '{}'...", themeCode, query);
            DeezerResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("limit", Math.min(limit, 100))
                            .build())
                    .retrieve()
                    .body(DeezerResponseDto.class);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                log.warn("Aucun résultat retourné par Deezer pour la requête : {}", query);
                return 0;
            }

            int importedCount = 0;
            for (DeezerResponseDto.DeezerTrackItem item : response.getData()) {
                // S'assurer que le morceau possède bien un extrait preview MP3
                if (item.getPreview() == null || item.getPreview().isBlank()) {
                    continue;
                }

                // Éviter les doublons par deezer_id
                if (trackRepository.findByDeezerId(item.getId()).isPresent()) {
                    continue;
                }

                String title = item.getTitle();
                String artistName = (item.getArtist() != null) ? item.getArtist().getName() : "Inconnu";
                String albumName = (item.getAlbum() != null) ? item.getAlbum().getTitle() : null;
                String albumCover = (item.getAlbum() != null) ? item.getAlbum().getCoverMedium() : null;

                Track track = Track.builder()
                        .deezerId(item.getId())
                        .title(title)
                        .normalizedTitle(StringNormalizer.normalize(title))
                        .artist(artistName)
                        .normalizedArtist(StringNormalizer.normalize(artistName))
                        .previewUrl(item.getPreview())
                        .albumName(albumName)
                        .albumCoverUrl(albumCover)
                        .theme(theme)
                        .altTitles(new ArrayList<>())
                        .altArtists(new ArrayList<>())
                        .isActive(true)
                        .build();

                trackRepository.save(track);
                importedCount++;
            }

            log.info("{} nouveaux morceaux importés avec succès pour le thème '{}'.", importedCount, themeCode);
            return importedCount;

        } catch (Exception e) {
            log.error("Erreur lors de la communication avec l'API Deezer : {}", e.getMessage(), e);
            throw new RuntimeException("Échec de l'import Deezer : " + e.getMessage(), e);
        }
    }

    public record CuratedTrackQuery(String artist, String track, List<String> altArtists, List<String> altTitles) {
        public CuratedTrackQuery(String artist, String track) {
            this(artist, track, List.of(), List.of());
        }
    }
    public Long getPlaylistIdForTheme(String themeCode) {
        return switch (themeCode) {
            case "ANNEES_80" -> playlist80s;
            case "ANNEES_60" -> playlist60s;
            case "ROCK" -> playlistRock;
            case "RAP_FR" -> playlistRapFr;
            case "POP" -> playlistPop;
            case "ANNEES_2000" -> playlist2000s;
            default -> null;
        };
    }

    /**
     * Charge le catalogue statique de secours depuis le fichier JSON.
     */
    public Map<String, List<CuratedTrackQuery>> loadStaticCuratedCatalogue() {
        try (var is = new ClassPathResource("curated-catalogue.json").getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<Map<String, List<CuratedTrackQuery>>>() {});
        } catch (Exception e) {
            log.error("Impossible de charger le catalogue statique JSON (curated-catalogue.json) : {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Importe dynamiquement les morceaux depuis une playlist Deezer officielle (Approche A).
     *
     * @param themeCode Code du thème (ex: 'ROCK')
     * @param playlistId ID de la playlist Deezer
     * @param limit Nombre max de morceaux à importer
     * @return Nombre de morceaux enregistrés
     */
    @Transactional
    public int importTracksFromPlaylist(String themeCode, long playlistId, int limit) {
        Theme theme = themeRepository.findByCode(themeCode)
                .orElseThrow(() -> new IllegalArgumentException("Thème introuvable : " + themeCode));

        RestClient restClient = RestClient.builder()
                .baseUrl(deezerBaseUrl)
                .build();

        try {
            log.info("Appel Deezer API : récupération de la playlist {} pour le thème '{}'...", playlistId, themeCode);
            DeezerResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/playlist/{id}/tracks")
                            .queryParam("limit", Math.min(limit, 100))
                            .build(playlistId))
                    .retrieve()
                    .body(DeezerResponseDto.class);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                log.warn("Aucun morceau retourné par Deezer pour la playlist {}", playlistId);
                return 0;
            }

            // Déduplication textuelle pour éviter les doublons avec des deezer_id différents
            List<Track> existingTracks = trackRepository.findByThemeId(theme.getId());
            Set<String> existingPairs = new HashSet<>();
            for (Track t : existingTracks) {
                existingPairs.add(t.getNormalizedArtist() + "::" + t.getNormalizedTitle());
            }

            int importedCount = 0;
            for (DeezerResponseDto.DeezerTrackItem item : response.getData()) {
                if (!isAcceptableTrack(item)) {
                    continue;
                }

                if (trackRepository.findByDeezerId(item.getId()).isPresent()) {
                    continue;
                }

                String cleanTitle = (item.getTitleShort() != null && !item.getTitleShort().isBlank())
                        ? item.getTitleShort()
                        : item.getTitle();

                cleanTitle = cleanTitle.replaceAll("(?i)\\s*-\\s*remaster(ed)?.*$", "")
                                       .replaceAll("(?i)\\s*\\(remaster(ed)?.*\\)$", "")
                                       .replaceAll("(?i)\\s*\\(radio edit\\)$", "")
                                       .trim();

                String artistName = (item.getArtist() != null && item.getArtist().getName() != null)
                        ? item.getArtist().getName()
                        : "Inconnu";

                String normTitle = StringNormalizer.normalize(cleanTitle);
                String normArtist = StringNormalizer.normalize(artistName);
                String pairKey = normArtist + "::" + normTitle;

                if (existingPairs.contains(pairKey)) {
                    continue;
                }
                existingPairs.add(pairKey);

                String albumName = (item.getAlbum() != null) ? item.getAlbum().getTitle() : null;
                String albumCover = (item.getAlbum() != null) ? item.getAlbum().getCoverMedium() : null;

                Track track = Track.builder()
                        .deezerId(item.getId())
                        .title(cleanTitle)
                        .normalizedTitle(normTitle)
                        .artist(artistName)
                        .normalizedArtist(normArtist)
                        .previewUrl(item.getPreview())
                        .albumName(albumName)
                        .albumCoverUrl(albumCover)
                        .theme(theme)
                        .altTitles(new ArrayList<>())
                        .altArtists(new ArrayList<>())
                        .isActive(true)
                        .build();

                trackRepository.save(track);
                importedCount++;
            }

            log.info("{} morceaux importés dynamiquement avec succès depuis la playlist Deezer {} pour le thème '{}'.",
                    importedCount, playlistId, themeCode);
            return importedCount;

        } catch (Exception e) {
            log.error("Erreur lors de l'import dynamique de la playlist Deezer {} : {}", playlistId, e.getMessage());
            return 0;
        }
    }

    /**
     * Alimente un thème en CUMULANT :
     * 1. La playlist dynamique Deezer (~50 morceaux)
     * 2. Le catalogue statique JSON (compléments cultes absents + fusion des alias)
     * Si l'import dynamique échoue (< 15), bascule sur le fallback statique complet.
     */
    @Transactional
    public int populateThemeWithFallback(String themeCode) {
        Theme theme = themeRepository.findByCode(themeCode)
                .orElseThrow(() -> new IllegalArgumentException("Thème introuvable : " + themeCode));

        int importedFromPlaylist = 0;
        Long playlistId = getPlaylistIdForTheme(themeCode);

        if (playlistId != null) {
            try {
                importedFromPlaylist = importTracksFromPlaylist(themeCode, playlistId, 60);
            } catch (Exception e) {
                log.warn("Échec de la récupération dynamique de la playlist pour {}: {}. Déclenchement du fallback.",
                        themeCode, e.getMessage());
            }
        }

        // Si l'import dynamique a échoué (< 15 titres), fallback statique complet
        if (importedFromPlaylist < 15) {
            log.warn("Moins de 15 morceaux obtenus dynamiquement pour '{}' ({} obtenus). Activation du fallback statique complet...",
                    themeCode, importedFromPlaylist);
            Map<String, List<CuratedTrackQuery>> staticCatalogue = loadStaticCuratedCatalogue();
            List<CuratedTrackQuery> fallbackList = staticCatalogue.get(themeCode);
            if (fallbackList != null && !fallbackList.isEmpty()) {
                int staticImported = importCuratedTracksForTheme(themeCode, fallbackList);
                return importedFromPlaylist + staticImported;
            }
            return importedFromPlaylist;
        }

        // Cumul vertueux : on enrichit la playlist avec les pépites et alias du catalogue statique !
        int curatedAdded = enrichThemeWithCuratedCatalogue(theme, themeCode);
        return importedFromPlaylist + curatedAdded;
    }

    /**
     * Enrichit un thème avec le catalogue statique JSON :
     * - Si le morceau est déjà présent : fusion des alias altArtists / altTitles sans appel réseau.
     * - Si le morceau est absent de la playlist : import direct depuis Deezer.
     */
    @Transactional
    public int enrichThemeWithCuratedCatalogue(Theme theme, String themeCode) {
        Map<String, List<CuratedTrackQuery>> staticCatalogue = loadStaticCuratedCatalogue();
        List<CuratedTrackQuery> curatedList = staticCatalogue.get(themeCode);
        if (curatedList == null || curatedList.isEmpty()) {
            return 0;
        }

        List<Track> existingTracks = trackRepository.findByThemeId(theme.getId());
        RestClient restClient = RestClient.builder().baseUrl(deezerBaseUrl).build();

        int newlyImported = 0;
        int aliasesMerged = 0;

        for (CuratedTrackQuery q : curatedList) {
            Track matchedTrack = findMatchingTrack(existingTracks, q);

            if (matchedTrack != null) {
                // Morceau déjà présent : fusion des alias sans appel réseau !
                boolean updated = false;
                if (q.altArtists() != null) {
                    for (String altArt : q.altArtists()) {
                        if (matchedTrack.getAltArtists().stream().noneMatch(a -> a.equalsIgnoreCase(altArt))) {
                            matchedTrack.getAltArtists().add(altArt);
                            updated = true;
                        }
                    }
                }
                if (q.altTitles() != null) {
                    for (String altTit : q.altTitles()) {
                        if (matchedTrack.getAltTitles().stream().noneMatch(t -> t.equalsIgnoreCase(altTit))) {
                            matchedTrack.getAltTitles().add(altTit);
                            updated = true;
                        }
                    }
                }
                if (updated) {
                    trackRepository.save(matchedTrack);
                    aliasesMerged++;
                }
            } else {
                // Morceau culte absent de la playlist : import ciblé sur Deezer
                boolean added = importSingleCuratedTrack(theme, q, restClient);
                if (added) {
                    newlyImported++;
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException ignored) {}
                }
            }
        }

        log.info("Enrichissement catalogue [{}] : {} nouveaux titres cultes ajoutés, {} titres enrichis avec alias.",
                themeCode, newlyImported, aliasesMerged);
        return newlyImported;
    }

    private Track findMatchingTrack(List<Track> existingTracks, CuratedTrackQuery q) {
        String qNormArtist = StringNormalizer.normalize(q.artist());
        String qNormTitle = StringNormalizer.normalize(q.track());

        for (Track t : existingTracks) {
            boolean artistMatches = t.getNormalizedArtist().equalsIgnoreCase(qNormArtist)
                    || (q.altArtists() != null && q.altArtists().stream().anyMatch(a -> StringNormalizer.normalize(a).equalsIgnoreCase(t.getNormalizedArtist())))
                    || t.getAltArtists().stream().anyMatch(a -> StringNormalizer.normalize(a).equalsIgnoreCase(qNormArtist));

            boolean titleMatches = t.getNormalizedTitle().equalsIgnoreCase(qNormTitle)
                    || (q.altTitles() != null && q.altTitles().stream().anyMatch(tit -> StringNormalizer.normalize(tit).equalsIgnoreCase(t.getNormalizedTitle())))
                    || t.getAltTitles().stream().anyMatch(tit -> StringNormalizer.normalize(tit).equalsIgnoreCase(qNormTitle));

            if (artistMatches && titleMatches) {
                return t;
            }
        }
        return null;
    }

    private boolean importSingleCuratedTrack(Theme theme, CuratedTrackQuery q, RestClient restClient) {
        try {
            String queryParam = String.format("artist:\"%s\" track:\"%s\"", q.artist(), q.track());
            DeezerResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", queryParam)
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(DeezerResponseDto.class);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                return false;
            }

            DeezerResponseDto.DeezerTrackItem best = response.getData().stream()
                    .filter(this::isAcceptableTrack)
                    .max(Comparator.comparingInt(item -> item.getRank() != null ? item.getRank() : 0))
                    .orElse(null);

            if (best == null) {
                best = response.getData().stream()
                        .filter(i -> i.getPreview() != null && !i.getPreview().isBlank())
                        .findFirst()
                        .orElse(null);
            }

            if (best == null || trackRepository.findByDeezerId(best.getId()).isPresent()) {
                return false;
            }

            String cleanTitle = (best.getTitleShort() != null && !best.getTitleShort().isBlank())
                    ? best.getTitleShort()
                    : best.getTitle();

            cleanTitle = cleanTitle.replaceAll("(?i)\\s*-\\s*remaster(ed)?.*$", "")
                                   .replaceAll("(?i)\\s*\\(remaster(ed)?.*\\)$", "")
                                   .replaceAll("(?i)\\s*\\(radio edit\\)$", "")
                                   .trim();

            String artistName = (best.getArtist() != null) ? best.getArtist().getName() : q.artist();
            String albumName = (best.getAlbum() != null) ? best.getAlbum().getTitle() : null;
            String albumCover = (best.getAlbum() != null) ? best.getAlbum().getCoverMedium() : null;

            List<String> altTitles = new ArrayList<>();
            if (q.altTitles() != null) altTitles.addAll(q.altTitles());
            if (!best.getTitle().equalsIgnoreCase(cleanTitle)) altTitles.add(best.getTitle());
            if (!q.track().equalsIgnoreCase(cleanTitle)) altTitles.add(q.track());

            List<String> altArtists = new ArrayList<>();
            if (q.altArtists() != null) altArtists.addAll(q.altArtists());

            Track track = Track.builder()
                    .deezerId(best.getId())
                    .title(cleanTitle)
                    .normalizedTitle(StringNormalizer.normalize(cleanTitle))
                    .artist(artistName)
                    .normalizedArtist(StringNormalizer.normalize(artistName))
                    .previewUrl(best.getPreview())
                    .albumName(albumName)
                    .albumCoverUrl(albumCover)
                    .theme(theme)
                    .altTitles(altTitles)
                    .altArtists(altArtists)
                    .isActive(true)
                    .build();

            trackRepository.save(track);
            log.info("Complément culte importé [{}] : {} - {}", theme.getCode(), artistName, cleanTitle);
            return true;
        } catch (Exception e) {
            log.warn("Erreur import complément culte {} - {} : {}", q.artist(), q.track(), e.getMessage());
            return false;
        }
    }

    private boolean isAcceptableTrack(DeezerResponseDto.DeezerTrackItem item) {
        if (item.getPreview() == null || item.getPreview().isBlank()) {
            return false;
        }
        String title = (item.getTitle() != null ? item.getTitle() : "").toLowerCase();
        String artist = (item.getArtist() != null && item.getArtist().getName() != null ? item.getArtist().getName() : "").toLowerCase();

        // Bannir les covers, karaoké, tributes, instrumentaux
        return !title.contains("karaoke") && !title.contains("tribute") && !title.contains("cover")
                && !title.contains("in the style of") && !title.contains("instrumental")
                && !title.contains("reprise") && !title.contains("sound-alike")
                && !artist.contains("karaoke") && !artist.contains("tribute") && !artist.contains("all-stars");
    }

    public int importCuratedTracksForTheme(String themeCode, List<CuratedTrackQuery> queries) {
        Theme theme = themeRepository.findByCode(themeCode)
                .orElseThrow(() -> new IllegalArgumentException("Thème introuvable : " + themeCode));

        RestClient restClient = RestClient.builder()
                .baseUrl(deezerBaseUrl)
                .build();

        int imported = 0;
        for (CuratedTrackQuery q : queries) {
            try {
                String queryParam = String.format("artist:\"%s\" track:\"%s\"", q.artist(), q.track());
                DeezerResponseDto response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/search")
                                .queryParam("q", queryParam)
                                .queryParam("limit", 10)
                                .build())
                        .retrieve()
                        .body(DeezerResponseDto.class);

                if (response == null || response.getData() == null || response.getData().isEmpty()) {
                    log.warn("Aucun résultat Deezer pour {} - {}", q.artist(), q.track());
                    continue;
                }

                // Filtrer pour éliminer les reprises, karaokés et retenir la piste avec la plus forte notoriété (rank)
                DeezerResponseDto.DeezerTrackItem best = response.getData().stream()
                        .filter(this::isAcceptableTrack)
                        .max(Comparator.comparingInt(item -> item.getRank() != null ? item.getRank() : 0))
                        .orElse(null);

                if (best == null) {
                    best = response.getData().stream()
                            .filter(i -> i.getPreview() != null && !i.getPreview().isBlank())
                            .findFirst()
                            .orElse(null);
                }

                if (best == null) continue;

                if (trackRepository.findByDeezerId(best.getId()).isPresent()) {
                    continue;
                }

                String cleanTitle = (best.getTitleShort() != null && !best.getTitleShort().isBlank())
                        ? best.getTitleShort()
                        : best.getTitle();

                cleanTitle = cleanTitle.replaceAll("(?i)\\s*-\\s*remaster(ed)?.*$", "")
                                       .replaceAll("(?i)\\s*\\(remaster(ed)?.*\\)$", "")
                                       .trim();

                String artistName = (best.getArtist() != null) ? best.getArtist().getName() : q.artist();
                String albumName = (best.getAlbum() != null) ? best.getAlbum().getTitle() : null;
                String albumCover = (best.getAlbum() != null) ? best.getAlbum().getCoverMedium() : null;

                List<String> altTitles = new ArrayList<>();
                if (q.altTitles() != null) {
                    altTitles.addAll(q.altTitles());
                }
                if (!best.getTitle().equalsIgnoreCase(cleanTitle)) {
                    altTitles.add(best.getTitle());
                }
                if (!q.track().equalsIgnoreCase(cleanTitle)) {
                    altTitles.add(q.track());
                }

                List<String> altArtists = new ArrayList<>();
                if (q.altArtists() != null) {
                    altArtists.addAll(q.altArtists());
                }

                Track track = Track.builder()
                        .deezerId(best.getId())
                        .title(cleanTitle)
                        .normalizedTitle(StringNormalizer.normalize(cleanTitle))
                        .artist(artistName)
                        .normalizedArtist(StringNormalizer.normalize(artistName))
                        .previewUrl(best.getPreview())
                        .albumName(albumName)
                        .albumCoverUrl(albumCover)
                        .theme(theme)
                        .altTitles(altTitles)
                        .altArtists(altArtists)
                        .isActive(true)
                        .build();

                trackRepository.save(track);
                imported++;
                log.info("Morceau culte importé [{}] : {} - {} (rank={})", themeCode, artistName, cleanTitle, best.getRank());

                Thread.sleep(60);

            } catch (Exception e) {
                log.warn("Erreur import morceau culte {} - {} : {}", q.artist(), q.track(), e.getMessage());
            }
        }
        return imported;
    }

    /**
     * Purge les anciennes pistes et réensemence avec l'approche dynamique (playlists) et fallback statique.
     */
    @Transactional
    public Map<String, Object> reseedCuratedCatalogue() {
        log.info("Purge de l'ancien catalogue et réensemencement dynamique (avec fallback statique)...");

        jdbcTemplate.execute("DELETE FROM match_rounds");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM tracks");

        int totalImported = 0;
        Map<String, Integer> countPerTheme = new HashMap<>();

        List<Theme> themes = themeRepository.findByIsActiveTrueOrderByCreatedAtAsc();
        for (Theme theme : themes) {
            String themeCode = theme.getCode();
            int imported = populateThemeWithFallback(themeCode);
            countPerTheme.put(themeCode, imported);
            totalImported += imported;
        }

        log.info("Réensemencement terminé avec succès ! Total de {} titres importés.", totalImported);
        return Map.of(
                "status", "SUCCESS",
                "totalTracks", totalImported,
                "themes", countPerTheme
        );
    }

    /**
     * Initialisation au démarrage : synchronise les morceaux si la base est vide ou contient un catalogue non cumulé (< 350 titres).
     */
    @Transactional
    public void seedInitialTracksIfEmpty() {
        long currentCount = trackRepository.count();
        if (currentCount >= 350) {
            log.info("La base contient déjà le catalogue cumulé complet ({} titres).", currentCount);
            return;
        }

        log.info("Base de données nécessitant une synchronisation cumulée ({} titres) : enrichissement playlists + catalogue culte...", currentCount);
        reseedCuratedCatalogue();
    }
}
