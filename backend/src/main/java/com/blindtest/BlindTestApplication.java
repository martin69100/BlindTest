package com.blindtest;

import com.blindtest.track.service.DeezerClientService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BlindTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlindTestApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDeezerTracks(DeezerClientService deezerClientService) {
        return args -> {
            // Peuplement automatique des titres Deezer au premier lancement si la table tracks est vide
            deezerClientService.seedInitialTracksIfEmpty();
        };
    }
}
