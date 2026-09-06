# 🎵 BeatRival - Blind Test Compétitif & Entraînement

Application web complète de Blind Test musical compétitif avec matchmaking ELO dynamique, reconnaissance audio temps réel (WebSockets STOMP), tolérance aux fautes d'orthographe (Damerau-Levenshtein), et extraits audio 30s propulsés par l'API publique Deezer.

---

## 🛠️ Stack Technique

- **Frontend** : React 19, TypeScript, Tailwind CSS, Zustand, Lucide Icons, Canvas Confetti.
- **Backend** : Java 21, Spring Boot 3.3, Spring WebSocket STOMP, Spring Security (Google OAuth2), Spring Data JPA, Flyway.
- **Base de données** : PostgreSQL 16 (avec Docker Compose).
- **Moteur Audio** : Extraits 30s de l'API publique Deezer (aucun compte requis pour les joueurs).

---

## 🚀 Démarrage Rapide

### 1. Base de données PostgreSQL

Lancez l'instance PostgreSQL via Docker :
```bash
docker compose up -d
```
*Si vous n'utilisez pas Docker, assurez-vous d'avoir une instance PostgreSQL locale sur le port 5432 avec une base nommée `blindtest`.*

### 2. Démarrer le Backend Spring Boot

```bash
cd backend
.\mvnw.cmd spring-boot:run
```
Le backend démarrera sur `http://localhost:8080`.
Les migrations Flyway (`V1__init_schema.sql` et `V2__seed_themes.sql`) s'exécutent automatiquement au démarrage pour initialiser les tables et les 6 thèmes musicaux.

### 3. Démarrer le Frontend React

```bash
cd frontend
npm run dev
```
L'interface sera accessible sur `http://localhost:5173`.

---

## 🎮 Fonctionnalités & Règles de Jeu

### 1. Sécurité & Anti-Triche (Zero-Knowledge)
- Durant la manche, **aucune métadonnée (titre, artiste, id Deezer) ne transite vers le frontend**. Le client reçoit uniquement l'URL opaque du MP3 et un identifiant de manche éphémère.
- L'arbitrage du premier buzzer est atomique (`AtomicBoolean`) côté serveur.
- La validation des propositions s'effectue exclusivement sur le serveur.

### 2. Gameplay & Système de Réponse
- **Durée d'écoute** : 20 secondes par manche, 10 manches par match.
- **Buzzer** : Raccourcis clavier `Entrée` ou `Espace` (ou clic sur le buzzer). La musique se met instantanément en pause pour les deux joueurs.
- **Saisie initiale (5s)** : Le joueur qui a buzzé a 5 secondes pour entrer **le Titre OU l'Artiste**.
  - Si validé : **+1 point** et déclenchement du chrono **Bonus de 10 secondes** pour tenter de trouver la 2ème information (**+1 point bonus**).
  - Si échec ou timeout (Option A - Vol de main) : La musique reprend pour les secondes restantes et **l'adversaire a l'opportunité de buzzer à son tour**.
- **Fuzzy Matching Intelligent** :
  - Pipeline de normalisation (`StringNormalizer`) : suppression des diacritiques/accents (*Céline -> Celine*), de la ponctuation (*P!nk -> p nk*, *AC/DC -> ac dc*), des mentions parasites (*feat.*, *remaster*, *live*, *edit*), et des articles de tête (*The, Les, Le...*).
  - Algorithme de **Damerau-Levenshtein** : tolérance adaptative selon la longueur du mot (0 faute pour $\le 3$ lettres, 1 faute pour 4-6 lettres, 2 fautes pour 7-11 lettres, 3 fautes au-delà) et inversion de frappe autorisée (*jhonny -> johnny*).

### 3. Matchmaking & Calcul d'ELO
- Tout nouveau joueur débute à **1000 ELO**.
- Formule ELO officielle FIDE avec facteur $K$ dynamique ($K=40$ pour les 20 premières parties de calibrage, $K=24$ en régime standard, $K=16$ au-delà de 2200).
- Recherche d'adversaire avec élargissement progressif : $\pm 50$ ELO au départ, s'élargissant de $+25$ points toutes les 3 secondes jusqu'à $\pm 300$ ELO max.

---

## 🧪 Tests Unitaires Backend

Pour exécuter la suite de tests unitaires du moteur de jeu (Fuzzy matching, normalisation et ELO) :
```bash
cd backend
.\mvnw.cmd test
```
Tous les 22 tests unitaires sont validés avec 0 échec.
