package com.blindtest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NeonDatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Vérifie la connectivité à Neon PostgreSQL et l'exécution des migrations Flyway")
    void testNeonConnection() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM themes")) {

            assertTrue(rs.next());
            int count = rs.getInt(1);
            System.out.println("=== Connexion Neon réussie ! Nombre de thèmes en base : " + count + " ===");
            assertTrue(count >= 6, "Les 6 thèmes initiaux doivent être insérés par Flyway V2");
        }
    }
}


