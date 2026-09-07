package com.blindtest.track;

import com.blindtest.track.service.DeezerClientService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CuratedCatalogueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testLoadStaticCuratedCatalogueJson() throws Exception {
        ClassPathResource resource = new ClassPathResource("curated-catalogue.json");
        assertTrue(resource.exists(), "Le fichier curated-catalogue.json doit exister dans classpath");

        try (InputStream is = resource.getInputStream()) {
            Map<String, List<DeezerClientService.CuratedTrackQuery>> catalogue =
                    objectMapper.readValue(is, new TypeReference<>() {});

            assertNotNull(catalogue);
            assertEquals(6, catalogue.size(), "Le catalogue doit contenir 6 thèmes");

            assertTrue(catalogue.containsKey("ANNEES_80"));
            assertTrue(catalogue.containsKey("ANNEES_60"));
            assertTrue(catalogue.containsKey("ROCK"));
            assertTrue(catalogue.containsKey("RAP_FR"));
            assertTrue(catalogue.containsKey("POP"));
            assertTrue(catalogue.containsKey("ANNEES_2000"));

            for (Map.Entry<String, List<DeezerClientService.CuratedTrackQuery>> entry : catalogue.entrySet()) {
                assertFalse(entry.getValue().isEmpty(), "Le thème " + entry.getKey() + " ne doit pas être vide");
                for (DeezerClientService.CuratedTrackQuery q : entry.getValue()) {
                    assertNotNull(q.artist(), "L'artiste ne doit pas être nul");
                    assertNotNull(q.track(), "Le titre ne doit pas être nul");
                }
            }
        }
    }
}
