package com.blindtest.track.entity;

import com.blindtest.theme.entity.Theme;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deezer_id", nullable = false, unique = true)
    private Long deezerId;

    @Column(nullable = false)
    private String title;

    @Column(name = "normalized_title", nullable = false)
    private String normalizedTitle;

    @Column(nullable = false)
    private String artist;

    @Column(name = "normalized_artist", nullable = false)
    private String normalizedArtist;

    @Column(name = "preview_url", nullable = false, columnDefinition = "TEXT")
    private String previewUrl;

    @Column(name = "album_name")
    private String albumName;

    @Column(name = "album_cover_url", columnDefinition = "TEXT")
    private String albumCoverUrl;

    @Column(name = "release_year")
    private Integer releaseYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theme_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Theme theme;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alt_titles", columnDefinition = "text[]")
    @Builder.Default
    private List<String> altTitles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alt_artists", columnDefinition = "text[]")
    @Builder.Default
    private List<String> altArtists = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
