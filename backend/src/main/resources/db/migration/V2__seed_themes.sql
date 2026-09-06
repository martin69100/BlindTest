-- Initialisation des Thèmes musicaux de base
INSERT INTO themes (id, code, name, description, icon_url, is_active)
VALUES
    (uuid_generate_v4(), 'ANNEES_80', 'Années 80', 'Les plus grands tubes synthpop, disco et new wave des années 80.', 'cassette', true),
    (uuid_generate_v4(), 'ANNEES_60', 'Années 60', 'L''âge d''or du rock, yéyé, soul et pop des sixties.', 'disc', true),
    (uuid_generate_v4(), 'ROCK', 'Rock & Métal', 'Des riffs légendaires de Led Zeppelin à Nirvana et Arctic Monkeys.', 'flame', true),
    (uuid_generate_v4(), 'POP', 'Pop Internationale', 'Les incontournables des hit-parades mondiaux d''hier et d''aujourd''hui.', 'sparkles', true),
    (uuid_generate_v4(), 'RAP_FR', 'Rap Français', 'Du rap des années 90 aux bangers actuels.', 'mic', true),
    (uuid_generate_v4(), 'ANNEES_2000', 'Années 2000', 'La nostalgie des années 2000 : RnB, boybands et tubes dancefloor.', 'radio', true)
ON CONFLICT (code) DO NOTHING;
