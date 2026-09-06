package com.blindtest.track.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeezerResponseDto {

    private List<DeezerTrackItem> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeezerTrackItem {
        private Long id;
        private String title;

        @JsonProperty("title_short")
        private String titleShort;

        private String preview;

        private Integer rank;

        @JsonProperty("duration")
        private Integer duration;

        private DeezerArtist artist;
        private DeezerAlbum album;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeezerArtist {
        private Long id;
        private String name;
        private String picture;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeezerAlbum {
        private Long id;
        private String title;

        @JsonProperty("cover_medium")
        private String coverMedium;
    }
}
