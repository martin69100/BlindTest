package com.blindtest.auth;

import com.blindtest.theme.entity.Theme;
import com.blindtest.theme.entity.UserThemeStats;
import com.blindtest.theme.repository.ThemeRepository;
import com.blindtest.theme.repository.UserThemeStatsRepository;
import com.blindtest.user.entity.User;
import com.blindtest.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final ThemeRepository themeRepository;
    private final UserThemeStatsRepository statsRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String googleId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        User user = userRepository.findByGoogleId(googleId).map(existing -> {
            existing.setDisplayName(name != null ? name : existing.getDisplayName());
            existing.setAvatarUrl(picture != null ? picture : existing.getAvatarUrl());
            existing.setLastActiveAt(Instant.now());
            return userRepository.save(existing);
        }).orElseGet(() -> {
            log.info("Création d'un nouvel utilisateur Google : {} ({})", name, email);
            User newUser = User.builder()
                    .googleId(googleId)
                    .email(email != null ? email : googleId + "@google.user")
                    .displayName(name != null ? name : "Joueur")
                    .avatarUrl(picture)
                    .elo(1000)
                    .createdAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .build();
            User saved = userRepository.save(newUser);

            // Initialiser les stats pour tous les thèmes actifs
            List<Theme> activeThemes = themeRepository.findByIsActiveTrueOrderByCreatedAtAsc();
            for (Theme theme : activeThemes) {
                UserThemeStats stats = UserThemeStats.builder()
                        .user(saved)
                        .theme(theme)
                        .soloGamesPlayed(0)
                        .soloCorrectAnswers(0)
                        .versusCorrectAnswers(0)
                        .bonusCorrectAnswers(0)
                        .masteryPoints(0)
                        .updatedAt(Instant.now())
                        .build();
                statsRepository.save(stats);
            }
            return saved;
        });

        return oAuth2User;
    }
}
