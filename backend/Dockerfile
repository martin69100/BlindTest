# Étape 1 : Build avec Maven 3.9 et Eclipse Temurin 21
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Mise en cache des dépendances Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Compilation et packaging de l'application (sans tests)
COPY src ./src
RUN mvn clean package -DskipTests -B

# Étape 2 : Image runtime légère avec Temurin 21 JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Sécurité : exécution sous utilisateur non-root
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# Copie du JAR exécutable
COPY --from=builder --chown=spring:spring /app/target/blindtest-backend-*.jar app.jar

# Render injecte la variable PORT (port d'écoute par défaut 8080)
ENV PORT=8080
EXPOSE ${PORT}

# Runtime léger optimisé pour Render (-Xmx384m pour respecter la limite de 512 Mo de RAM)
ENTRYPOINT ["sh", "-c", "java -Xmx384m -Xms128m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dserver.port=${PORT} -jar app.jar"]
