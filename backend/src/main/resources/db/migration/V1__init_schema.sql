-- Extension UUID native pour PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table des Utilisateurs
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    google_id VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    elo INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_elo ON users (elo DESC);
CREATE INDEX idx_users_google_id ON users (google_id);

-- Table des Thèmes musicaux
CREATE TABLE themes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Statistiques et niveau de maîtrise par Thème pour chaque joueur
CREATE TABLE user_theme_stats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theme_id UUID NOT NULL REFERENCES themes(id) ON DELETE CASCADE,
    solo_games_played INTEGER NOT NULL DEFAULT 0,
    solo_correct_answers INTEGER NOT NULL DEFAULT 0,
    versus_correct_answers INTEGER NOT NULL DEFAULT 0,
    bonus_correct_answers INTEGER NOT NULL DEFAULT 0,
    mastery_points INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_theme UNIQUE (user_id, theme_id)
);

CREATE INDEX idx_user_theme_stats_lookup ON user_theme_stats (user_id, theme_id);

-- Morceaux musicaux importés depuis Deezer
CREATE TABLE tracks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    deezer_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    normalized_title VARCHAR(255) NOT NULL,
    artist VARCHAR(255) NOT NULL,
    normalized_artist VARCHAR(255) NOT NULL,
    preview_url TEXT NOT NULL,
    album_name VARCHAR(255),
    album_cover_url TEXT,
    release_year INTEGER,
    theme_id UUID NOT NULL REFERENCES themes(id) ON DELETE RESTRICT,
    alt_titles TEXT[] DEFAULT '{}',
    alt_artists TEXT[] DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracks_theme_active ON tracks (theme_id, is_active);
CREATE INDEX idx_tracks_deezer_id ON tracks (deezer_id);

-- Parties en mode Versus (Classé)
CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    player1_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    player2_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    winner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    player1_score INTEGER NOT NULL DEFAULT 0,
    player2_score INTEGER NOT NULL DEFAULT 0,
    player1_elo_before INTEGER NOT NULL,
    player2_elo_before INTEGER NOT NULL,
    player1_elo_change INTEGER NOT NULL DEFAULT 0,
    player2_elo_change INTEGER NOT NULL DEFAULT 0,
    theme_id UUID REFERENCES themes(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    CONSTRAINT chk_different_players CHECK (player1_id <> player2_id)
);

CREATE INDEX idx_matches_players ON matches (player1_id, player2_id, started_at DESC);
CREATE INDEX idx_matches_status ON matches (status);

-- Détail par manche pour analyse et historique
CREATE TABLE match_rounds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE RESTRICT,
    buzzer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reaction_time_ms INTEGER,
    first_guess VARCHAR(255),
    first_guess_correct BOOLEAN DEFAULT FALSE,
    first_guess_type VARCHAR(20),
    bonus_guess VARCHAR(255),
    bonus_guess_correct BOOLEAN DEFAULT FALSE,
    points_awarded INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_match_round UNIQUE (match_id, round_number)
);

CREATE INDEX idx_match_rounds_match ON match_rounds (match_id);
