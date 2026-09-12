package com.blindtest.track.service;

import com.blindtest.game.fuzzymatch.StringNormalizer;
import com.blindtest.track.entity.Track;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistImportService {

    private final ObjectMapper objectMapper;

    private static final Pattern DEEZER_URL_PATTERN = Pattern.compile(
            "(?:deezer\\.com/(?:[a-zA-Z_-]+/)?playlist/|deezer\\.page\\.link/)(\\d+)"
    );
    private static final Pattern DEEZER_ID_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern SPOTIFY_URL_PATTERN = Pattern.compile(
            "open\\.spotify\\.com/playlist/([a-zA-Z0-9]+)"
    );

    public record CustomPlaylistResult(String title, List<Track> tracks, String provider) {}

    /**
     * Analyse un lien de playlist Deezer ou Spotify publique et extrait les morceaux jouables (avec preview 30s)
     * sans nécessiter de compte développeur ni de clé API.
     */
    public CustomPlaylistResult importPlaylist(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Veuillez renseigner un lien de playlist valide.");
        }
        String url = rawUrl.trim();

        // 1. Vérification Deezer
        Matcher deezerMatcher = DEEZER_URL_PATTERN.matcher(url);
        if (deezerMatcher.find()) {
            String playlistId = deezerMatcher.group(1);
            return importDeezerPlaylist(playlistId);
        }
        if (DEEZER_ID_ONLY.matcher(url).matches()) {
            return importDeezerPlaylist(url);
        }

        // 2. Vérification Spotify
        Matcher spotifyMatcher = SPOTIFY_URL_PATTERN.matcher(url);
        if (spotifyMatcher.find()) {
            String playlistId = spotifyMatcher.group(1);
            return importSpotifyPlaylist(playlistId);
        }

        throw new IllegalArgumentException("Lien non reconnu. Seuls les liens de playlists publiques Deezer ou Spotify sont pris en charge.");
    }

    private CustomPlaylistResult importDeezerPlaylist(String playlistId) {
        log.info("Importation directe de la playlist Deezer ID: {}", playlistId);
        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.deezer.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "BlindTest/1.0 (Deezer Public API)")
                .build();

        try {
            String jsonStr = restClient.get()
                    .uri("/playlist/{id}", playlistId)
                    .retrieve()
                    .body(String.class);

            if (jsonStr == null || jsonStr.isBlank()) {
                throw new IllegalArgumentException("Réponse vide de l'API Deezer.");
            }

            JsonNode root = objectMapper.readTree(jsonStr);
            if (root.has("error")) {
                String errorMsg = root.get("error").path("message").asText("Playlist introuvable ou privée.");
                throw new IllegalArgumentException("Erreur Deezer : " + errorMsg);
            }

            String playlistTitle = root.path("title").asText("Playlist Deezer");
            JsonNode tracksNode = root.path("tracks").path("data");

            List<Track> tracks = new ArrayList<>();
            if (tracksNode.isArray()) {
                for (JsonNode item : tracksNode) {
                    String preview = item.path("preview").asText(null);
                    if (preview == null || preview.isBlank()) {
                        continue; // Morceau sans extrait 30s disponible
                    }

                    String title = item.path("title").asText("Inconnu");
                    String artist = item.path("artist").path("name").asText("Inconnu");
                    String album = item.path("album").path("title").asText(null);
                    String cover = item.path("album").path("cover_medium").asText(null);
                    long deezerId = item.path("id").asLong(0L);

                    Track track = Track.builder()
                            .id(UUID.randomUUID())
                            .deezerId(deezerId)
                            .title(title)
                            .normalizedTitle(StringNormalizer.normalize(title))
                            .artist(artist)
                            .normalizedArtist(StringNormalizer.normalize(artist))
                            .previewUrl(preview)
                            .albumName(album)
                            .albumCoverUrl(cover)
                            .altTitles(new ArrayList<>())
                            .altArtists(new ArrayList<>())
                            .isActive(true)
                            .build();

                    tracks.add(track);
                }
            }

            if (tracks.isEmpty()) {
                throw new IllegalArgumentException("Aucun morceau avec extrait 30s n'a pu être extrait de cette playlist Deezer.");
            }

            log.info("Playlist Deezer '{}' importée avec succès : {} morceaux jouables.", playlistTitle, tracks.size());
            return new CustomPlaylistResult(playlistTitle, tracks, "DEEZER");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur lors de l'import Deezer : {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de charger la playlist Deezer : " + e.getMessage(), e);
        }
    }

    private CustomPlaylistResult importSpotifyPlaylist(String playlistId) {
        log.info("Importation de la playlist Spotify ID: {} via embed public...", playlistId);
        RestClient restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();

        try {
            String html = restClient.get()
                    .uri("https://open.spotify.com/embed/playlist/" + playlistId)
                    .retrieve()
                    .body(String.class);

            if (html == null || !html.contains("__NEXT_DATA__")) {
                throw new IllegalArgumentException("Impossible de lire les informations publiques de cette playlist Spotify.");
            }

            int idx = html.indexOf("__NEXT_DATA__");
            int start = html.indexOf('>', idx) + 1;
            int end = html.indexOf("</script>", start);
            String jsonStr = html.substring(start, end);

            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode entity = root.path("props").path("pageProps").path("state").path("data").path("entity");
            String playlistTitle = entity.path("name").asText("Playlist Spotify");
            JsonNode trackList = entity.path("trackList");

            if (!trackList.isArray() || trackList.isEmpty()) {
                throw new IllegalArgumentException("La playlist Spotify ne contient aucun titre ou est privée.");
            }

            // Pour chaque morceau Spotify (limité aux 35 premiers pour garantir une importation rapide),
            // on recherche l'extrait audio 30s correspondant sur Deezer
            RestClient deezerSearchClient = RestClient.builder()
                    .baseUrl("https://api.deezer.com")
                    .defaultHeader(HttpHeaders.USER_AGENT, "BlindTest/1.0")
                    .build();

            List<Track> matchedTracks = new ArrayList<>();
            int maxItems = Math.min(trackList.size(), 35);

            for (int i = 0; i < maxItems; i++) {
                JsonNode item = trackList.get(i);
                String title = item.path("title").asText("");
                String subtitle = item.path("subtitle").asText(""); // Noms d'artistes séparés par des virgules
                if (title.isBlank()) continue;

                String primaryArtist = subtitle.split(",")[0].trim();
                String query = primaryArtist + " " + title;

                try {
                    String searchRes = deezerSearchClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/search")
                                    .queryParam("q", query)
                                    .queryParam("limit", 1)
                                    .build())
                            .retrieve()
                            .body(String.class);

                    if (searchRes != null) {
                        JsonNode searchRoot = objectMapper.readTree(searchRes);
                        JsonNode data = searchRoot.path("data");
                        if (data.isArray() && !data.isEmpty()) {
                            JsonNode match = data.get(0);
                            String preview = match.path("preview").asText(null);
                            if (preview != null && !preview.isBlank()) {
                                Track track = Track.builder()
                                        .id(UUID.randomUUID())
                                        .deezerId(match.path("id").asLong(0L))
                                        .title(match.path("title").asText(title))
                                        .normalizedTitle(StringNormalizer.normalize(match.path("title").asText(title)))
                                        .artist(match.path("artist").path("name").asText(primaryArtist))
                                        .normalizedArtist(StringNormalizer.normalize(match.path("artist").path("name").asText(primaryArtist)))
                                        .previewUrl(preview)
                                        .albumName(match.path("album").path("title").asText(null))
                                        .albumCoverUrl(match.path("album").path("cover_medium").asText(null))
                                        .altTitles(new ArrayList<>())
                                        .altArtists(new ArrayList<>())
                                        .isActive(true)
                                        .build();

                                matchedTracks.add(track);
                            }
                        }
                    }
                } catch (Exception err) {
                    log.warn("Matching Deezer ignoré pour '{}' - '{}' : {}", primaryArtist, title, err.getMessage());
                }
            }

            if (matchedTracks.isEmpty()) {
                throw new IllegalArgumentException("Impossible de trouver des extraits audio correspondants pour les titres de cette playlist Spotify.");
            }

            log.info("Playlist Spotify '{}' importée avec succès : {} morceaux associés.", playlistTitle, matchedTracks.size());
            return new CustomPlaylistResult(playlistTitle, matchedTracks, "SPOTIFY");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur lors de l'import Spotify : {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de charger la playlist Spotify : " + e.getMessage(), e);
        }
    }
}
