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

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeezerClientService {

    private final TrackRepository trackRepository;
    private final ThemeRepository themeRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${blindtest.deezer.base-url:https://api.deezer.com}")
    private String deezerBaseUrl;

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

    private final Map<String, List<CuratedTrackQuery>> curatedCatalogue = Map.of(
            "ANNEES_80", List.of(
                    new CuratedTrackQuery("Michael Jackson", "Billie Jean"),
                    new CuratedTrackQuery("Michael Jackson", "Beat It"),
                    new CuratedTrackQuery("Michael Jackson", "Thriller"),
                    new CuratedTrackQuery("Queen", "Another One Bites the Dust"),
                    new CuratedTrackQuery("A-ha", "Take On Me"),
                    new CuratedTrackQuery("Madonna", "Like a Virgin"),
                    new CuratedTrackQuery("Cyndi Lauper", "Girls Just Want to Have Fun"),
                    new CuratedTrackQuery("Wham!", "Wake Me Up Before You Go-Go"),
                    new CuratedTrackQuery("Whitney Houston", "I Wanna Dance with Somebody"),
                    new CuratedTrackQuery("Eurythmics", "Sweet Dreams"),
                    new CuratedTrackQuery("The Police", "Every Breath You Take", List.of("Police"), List.of()),
                    new CuratedTrackQuery("Bon Jovi", "Livin' on a Prayer"),
                    new CuratedTrackQuery("Indochine", "L'Aventurier"),
                    new CuratedTrackQuery("Daniel Balavoine", "Tous les cris les S.O.S", List.of("Balavoine"), List.of("Tous les cris")),
                    new CuratedTrackQuery("Jean-Jacques Goldman", "Envole-moi", List.of("Goldman", "JJG", "Jean Jacques Goldman"), List.of()),
                    new CuratedTrackQuery("Desireless", "Voyage Voyage"),
                    new CuratedTrackQuery("Début de Soirée", "Nuit de folie", List.of("Debut de Soiree"), List.of()),
                    new CuratedTrackQuery("Téléphone", "Cendrillon"),
                    new CuratedTrackQuery("Europe", "The Final Countdown"),
                    new CuratedTrackQuery("Survivor", "Eye of the Tiger"),
                    new CuratedTrackQuery("Rick Astley", "Never Gonna Give You Up"),
                    new CuratedTrackQuery("Earth, Wind & Fire", "Let's Groove", List.of("Earth Wind and Fire", "EWF"), List.of()),
                    new CuratedTrackQuery("Toto", "Africa"),
                    new CuratedTrackQuery("George Michael", "Careless Whisper"),
                    new CuratedTrackQuery("France Gall", "Ella, elle l'a"),
                    new CuratedTrackQuery("Gilbert Montagné", "Les sunlights des tropiques")
            ),
            "ANNEES_2000", List.of(
                    new CuratedTrackQuery("Britney Spears", "Toxic"),
                    new CuratedTrackQuery("Britney Spears", "Oops!... I Did It Again"),
                    new CuratedTrackQuery("Eminem", "Lose Yourself"),
                    new CuratedTrackQuery("Eminem", "Without Me"),
                    new CuratedTrackQuery("Daft Punk", "One More Time"),
                    new CuratedTrackQuery("Daft Punk", "Harder, Better, Faster, Stronger"),
                    new CuratedTrackQuery("Beyoncé", "Crazy in Love"),
                    new CuratedTrackQuery("Shakira", "Whenever, Wherever"),
                    new CuratedTrackQuery("The Black Eyed Peas", "I Gotta Feeling", List.of("Black Eyed Peas", "BEP"), List.of("Gotta Feeling")),
                    new CuratedTrackQuery("The Black Eyed Peas", "Where Is the Love?", List.of("Black Eyed Peas", "BEP"), List.of()),
                    new CuratedTrackQuery("Rihanna", "Umbrella"),
                    new CuratedTrackQuery("OutKast", "Hey Ya!"),
                    new CuratedTrackQuery("Linkin Park", "In the End"),
                    new CuratedTrackQuery("Coldplay", "Viva La Vida"),
                    new CuratedTrackQuery("Gorillaz", "Feel Good Inc."),
                    new CuratedTrackQuery("Kyo", "Dernière danse"),
                    new CuratedTrackQuery("Diam's", "La Boulette"),
                    new CuratedTrackQuery("Fatal Bazooka", "Fous ta cagoule"),
                    new CuratedTrackQuery("Yannick", "Ces soirées-là"),
                    new CuratedTrackQuery("O-Zone", "Dragostea Din Tei", List.of("Ozone"), List.of("Dragostea", "Numa Numa", "Mai Ya Hi")),
                    new CuratedTrackQuery("Lady Gaga", "Poker Face"),
                    new CuratedTrackQuery("Green Day", "Boulevard of Broken Dreams"),
                    new CuratedTrackQuery("The Killers", "Mr. Brightside"),
                    new CuratedTrackQuery("Evanescence", "Bring Me to Life"),
                    new CuratedTrackQuery("Avril Lavigne", "Complicated"),
                    new CuratedTrackQuery("Mika", "Relax, Take It Easy"),
                    new CuratedTrackQuery("Alizée", "Moi... Lolita")
            ),
            "ROCK", List.of(
                    new CuratedTrackQuery("AC/DC", "Highway to Hell", List.of("ACDC", "AC DC"), List.of()),
                    new CuratedTrackQuery("AC/DC", "Back in Black", List.of("ACDC", "AC DC"), List.of()),
                    new CuratedTrackQuery("Nirvana", "Smells Like Teen Spirit", List.of(), List.of("Teen Spirit")),
                    new CuratedTrackQuery("Nirvana", "Come as You Are"),
                    new CuratedTrackQuery("Queen", "Bohemian Rhapsody"),
                    new CuratedTrackQuery("Queen", "We Will Rock You"),
                    new CuratedTrackQuery("Guns N' Roses", "Sweet Child O' Mine", List.of("Guns and Roses", "Guns N Roses", "GNR", "Guns"), List.of()),
                    new CuratedTrackQuery("The Cranberries", "Zombie", List.of("Cranberries"), List.of()),
                    new CuratedTrackQuery("Red Hot Chili Peppers", "Californication", List.of("Red Hot", "RHCP"), List.of()),
                    new CuratedTrackQuery("Red Hot Chili Peppers", "Can't Stop", List.of("Red Hot", "RHCP"), List.of()),
                    new CuratedTrackQuery("Metallica", "Enter Sandman"),
                    new CuratedTrackQuery("The Rolling Stones", "Paint It, Black", List.of("Rolling Stones", "Stones"), List.of("Paint It Black")),
                    new CuratedTrackQuery("Deep Purple", "Smoke on the Water"),
                    new CuratedTrackQuery("Scorpions", "Still Loving You"),
                    new CuratedTrackQuery("Linkin Park", "Numb"),
                    new CuratedTrackQuery("Arctic Monkeys", "Do I Wanna Know?"),
                    new CuratedTrackQuery("The White Stripes", "Seven Nation Army"),
                    new CuratedTrackQuery("Oasis", "Wonderwall"),
                    new CuratedTrackQuery("Muse", "Uprising", List.of("Muzz", "Muze", "Muz"), List.of()),
                    new CuratedTrackQuery("Blink-182", "All the Small Things"),
                    new CuratedTrackQuery("Rage Against the Machine", "Killing in the Name", List.of("RATM", "Rage Against"), List.of("Killing in the Name of")),
                    new CuratedTrackQuery("Radiohead", "Creep"),
                    new CuratedTrackQuery("Téléphone", "Un autre monde"),
                    new CuratedTrackQuery("System of a Down", "Chop Suey!", List.of("SOAD", "System"), List.of()),
                    new CuratedTrackQuery("Trust", "Antisocial")
            ),
            "RAP_FR", List.of(
                    new CuratedTrackQuery("IAM", "Petit frère", List.of("I.A.M", "I A M"), List.of()),
                    new CuratedTrackQuery("IAM", "Je danse le Mia", List.of("I.A.M", "I A M"), List.of("Le Mia", "Mia")),
                    new CuratedTrackQuery("Suprême NTM", "Seine Saint-Denis Style", List.of("NTM", "Supreme NTM", "Supreme"), List.of("Seine Saint Denis")),
                    new CuratedTrackQuery("Suprême NTM", "Ma Benz", List.of("NTM", "Supreme NTM", "Supreme"), List.of()),
                    new CuratedTrackQuery("Booba", "Boulbi"),
                    new CuratedTrackQuery("Booba", "Numéro 10"),
                    new CuratedTrackQuery("Booba", "DKR"),
                    new CuratedTrackQuery("Orelsan", "Basique"),
                    new CuratedTrackQuery("Orelsan", "La terre est ronde"),
                    new CuratedTrackQuery("MC Solaar", "Caroline"),
                    new CuratedTrackQuery("Soprano", "Cosmo"),
                    new CuratedTrackQuery("Jul", "Tchikita"),
                    new CuratedTrackQuery("13 Organisé", "Bande Organisée"),
                    new CuratedTrackQuery("PNL", "Au DD"),
                    new CuratedTrackQuery("Sexion d'Assaut", "Désolé", List.of("Sexion"), List.of()),
                    new CuratedTrackQuery("Gims", "Bella"),
                    new CuratedTrackQuery("Gims", "Sapés comme jamais"),
                    new CuratedTrackQuery("Damso", "Macarena"),
                    new CuratedTrackQuery("Ninho", "Lettre à une femme"),
                    new CuratedTrackQuery("Nekfeu", "On verra"),
                    new CuratedTrackQuery("113", "Tonton du bled"),
                    new CuratedTrackQuery("Doc Gynéco", "Dans ma rue"),
                    new CuratedTrackQuery("Bigflo & Oli", "Dommage", List.of("Bigflo", "Oli", "Bigflo et Oli", "Big flo et oli", "Big flo & oli", "Bigflo Oli"), List.of()),
                    new CuratedTrackQuery("Sniper", "Gravé dans la roche"),
                    new CuratedTrackQuery("La Fouine", "Tous les mêmes")
            ),
            "POP", List.of(
                    new CuratedTrackQuery("The Weeknd", "Blinding Lights"),
                    new CuratedTrackQuery("The Weeknd", "Starboy"),
                    new CuratedTrackQuery("Bruno Mars", "Uptown Funk"),
                    new CuratedTrackQuery("Dua Lipa", "Levitating"),
                    new CuratedTrackQuery("Lady Gaga", "Bad Romance"),
                    new CuratedTrackQuery("Ed Sheeran", "Shape of You"),
                    new CuratedTrackQuery("Adele", "Rolling in the Deep"),
                    new CuratedTrackQuery("Sia", "Chandelier"),
                    new CuratedTrackQuery("Justin Timberlake", "Can't Stop the Feeling!"),
                    new CuratedTrackQuery("Billie Eilish", "bad guy"),
                    new CuratedTrackQuery("Katy Perry", "Firework"),
                    new CuratedTrackQuery("Maroon 5", "Sugar"),
                    new CuratedTrackQuery("Stromae", "Alors on danse"),
                    new CuratedTrackQuery("Stromae", "Papaoutai"),
                    new CuratedTrackQuery("Céline Dion", "Pour que tu m'aimes encore"),
                    new CuratedTrackQuery("David Guetta", "Titanium"),
                    new CuratedTrackQuery("Avicii", "Wake Me Up"),
                    new CuratedTrackQuery("Angèle", "Balance ton quoi"),
                    new CuratedTrackQuery("Clara Luciani", "La Grenade"),
                    new CuratedTrackQuery("Shakira", "Waka Waka"),
                    new CuratedTrackQuery("Harry Styles", "As It Was")
            ),
            "ANNEES_60", List.of(
                    new CuratedTrackQuery("The Beatles", "Hey Jude", List.of("Beatles"), List.of()),
                    new CuratedTrackQuery("The Beatles", "Let It Be", List.of("Beatles"), List.of()),
                    new CuratedTrackQuery("The Beatles", "Help!", List.of("Beatles"), List.of()),
                    new CuratedTrackQuery("Elvis Presley", "Jailhouse Rock"),
                    new CuratedTrackQuery("Elvis Presley", "Can't Help Falling in Love"),
                    new CuratedTrackQuery("The Rolling Stones", "(I Can't Get No) Satisfaction", List.of("Rolling Stones", "Stones"), List.of("Satisfaction")),
                    new CuratedTrackQuery("Claude François", "Belles! Belles! Belles!", List.of("Cloclo", "Claude Francois"), List.of()),
                    new CuratedTrackQuery("France Gall", "Poupée de cire poupée de son"),
                    new CuratedTrackQuery("Johnny Hallyday", "Noir c'est noir", List.of("Johnny", "Hallyday"), List.of()),
                    new CuratedTrackQuery("Jacques Brel", "Ne me quitte pas"),
                    new CuratedTrackQuery("Jacques Brel", "Amsterdam"),
                    new CuratedTrackQuery("The Beach Boys", "Good Vibrations"),
                    new CuratedTrackQuery("The Mamas & The Papas", "California Dreamin'"),
                    new CuratedTrackQuery("The Doors", "Light My Fire"),
                    new CuratedTrackQuery("Simon & Garfunkel", "The Sound of Silence", List.of("Simon and Garfunkel", "Simon et Garfunkel"), List.of("Sound of Silence")),
                    new CuratedTrackQuery("Aretha Franklin", "Respect"),
                    new CuratedTrackQuery("Ben E. King", "Stand by Me"),
                    new CuratedTrackQuery("Ray Charles", "Hit the Road Jack"),
                    new CuratedTrackQuery("Otis Redding", "(Sittin' On) The Dock of the Bay"),
                    new CuratedTrackQuery("Serge Gainsbourg", "Bonnie and Clyde"),
                    new CuratedTrackQuery("Nino Ferrer", "Le Téléfon"),
                    new CuratedTrackQuery("Michel Delpech", "Wight Is Wight")
            )
    );

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
     * Purge les anciennes pistes obscures et réensemence avec 100% de morceaux cultes de blind test.
     */
    @Transactional
    public Map<String, Object> reseedCuratedCatalogue() {
        log.info("Purge de l'ancien catalogue et réensemencement avec les méga-tubes cultes...");

        jdbcTemplate.execute("DELETE FROM match_rounds");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM tracks");

        int totalImported = 0;
        Map<String, Integer> countPerTheme = new HashMap<>();

        for (Map.Entry<String, List<CuratedTrackQuery>> entry : curatedCatalogue.entrySet()) {
            String themeCode = entry.getKey();
            int imported = importCuratedTracksForTheme(themeCode, entry.getValue());
            countPerTheme.put(themeCode, imported);
            totalImported += imported;
        }

        log.info("Réensemencement terminé avec succès ! Total de {} méga-tubes importés.", totalImported);
        return Map.of(
                "status", "SUCCESS",
                "totalTracks", totalImported,
                "themes", countPerTheme
        );
    }

    /**
     * Initialisation au démarrage si la base est vide.
     */
    @Transactional
    public void seedInitialTracksIfEmpty() {
        if (trackRepository.count() > 0) {
            log.info("La base contient déjà des morceaux ({} titres).", trackRepository.count());
            return;
        }

        log.info("Base de données vide : lancement de l'importation des morceaux cultes...");
        reseedCuratedCatalogue();
    }
}
